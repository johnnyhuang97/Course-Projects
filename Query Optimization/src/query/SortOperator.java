package edu.berkeley.cs186.database.query;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.common.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.common.Pair;
import edu.berkeley.cs186.database.io.Page;

import java.util.*;

public class SortOperator {
    private Database.Transaction transaction;
    private String tableName;
    private Comparator<Record> comparator;
    private Schema operatorSchema;
    private int numBuffers;
    private String sortedTableName = null;

    public SortOperator(Database.Transaction transaction, String tableName,
                        Comparator<Record> comparator) throws DatabaseException, QueryPlanException {
        this.transaction = transaction;
        this.tableName = tableName;
        this.comparator = comparator;
        this.operatorSchema = this.computeSchema();
        this.numBuffers = this.transaction.getNumMemoryPages();
    }

    public Schema computeSchema() throws QueryPlanException {
        try {
            return this.transaction.getFullyQualifiedSchema(this.tableName);
        } catch (DatabaseException de) {
            throw new QueryPlanException(de);
        }
    }

    public class Run {
        String tempTableName;

        public Run() throws DatabaseException {
            this.tempTableName = SortOperator.this.transaction.createTempTable(
                                     SortOperator.this.operatorSchema);
        }

        public void addRecord(List<DataBox> values) throws DatabaseException {
            SortOperator.this.transaction.addRecord(this.tempTableName, values);
        }

        public void addRecords(List<Record> records) throws DatabaseException {
            for (Record r : records) {
                this.addRecord(r.getValues());
            }
        }

        public Iterator<Record> iterator() throws DatabaseException {
            return SortOperator.this.transaction.getRecordIterator(this.tempTableName);
        }

        public String tableName() {
            return this.tempTableName;
        }
    }

    /**
     * Returns a NEW run that is the sorted version of the input run.
     * Can do an in memory sort over all the records in this run
     * using one of Java's built-in sorting methods.
     * Note: Don't worry about modifying the original run.
     * Returning a new run would bring one extra page in memory beyond the
     * size of the buffer, but it is done this way for ease.
     */
    public Run sortRun(Run run) throws DatabaseException {
        Iterator<Record> iterator = run.iterator();
        List<Record> recordList = new ArrayList<>();
        Run sorted = createRun();

        while (iterator.hasNext()) {
            Record record = iterator.next();
            recordList.add(record);
        }

        recordList.sort(comparator);
        sorted.addRecords(recordList);
        return sorted;
    }

    /**
     * Given a list of sorted runs, returns a new run that is the result
     * of merging the input runs. You should use a Priority Queue (java.util.PriorityQueue)
     * to determine which record should be should be added to the output run next.
     * It is recommended that your Priority Queue hold Pair<Record, Integer> objects
     * where a Pair (r, i) is the Record r with the smallest value you are
     * sorting on currently unmerged from run i.
     */
    public Run mergeSortedRuns(List<Run> runs) throws DatabaseException {
        PriorityQueue<Pair<Record, Integer>> priorityQueue = new PriorityQueue<>(new RecordPairComparator());
        Run merged = createRun();

        for (int i = 0; i < runs.size(); i++) {
            Iterator<Record> currentIterator = runs.get(i).iterator();

            while (currentIterator.hasNext()) {
                Record nextRecord = currentIterator.next();
                priorityQueue.add(new Pair<>(nextRecord, i));
            }
        }

        while(priorityQueue.size() > 0 ) {
            Pair<Record, Integer> pair = priorityQueue.poll();
            Record record = pair.getFirst();
            merged.addRecord(record.getValues());
        }
        return merged;
    }

    /**
     * Given a list of N sorted runs, returns a list of
     * sorted runs that is the result of merging (numBuffers - 1)
     * of the input runs at a time.
     */
    public List<Run> mergePass(List<Run> runs) throws DatabaseException {
        List<Run> mergedRuns = new ArrayList<>();
        int N = runs.size();
        int increment = numBuffers - 1;

        for (int i = 0; i < N; i+= increment) {
            int index = Math.min(N, i + increment);
            mergedRuns.add(mergeSortedRuns(runs.subList(i, index)));
        }

        return mergedRuns;
    }

    /**
     * Does an external merge sort on the table with name tableName
     * using numBuffers.
     * Returns the name of the table that backs the final run.
     */
    public String sort() throws DatabaseException {
        List<Run> sortedRuns = new ArrayList<>();
        BacktrackingIterator<Record> recordIterator = null;
        BacktrackingIterator<Page> pageIterator = null;

        pageIterator = this.transaction.getPageIterator(this.tableName);
        pageIterator.next();

        while (pageIterator.hasNext()) {
            recordIterator = this.transaction.getBlockIterator(this.tableName, pageIterator, this.numBuffers);

            Run run = createRun();
            while (recordIterator.hasNext()) {
                Record record = recordIterator.next();
                run.addRecord(record.getValues());
            }
            run = sortRun(run);
            sortedRuns.add(run);
        }

        List<Run> mergedRuns = mergePass(sortedRuns);

        while (mergedRuns.size() > 1) {
            sortedRuns = mergePass(sortedRuns);
        }

        return sortedRuns.get(0).tableName();

    }

    public Iterator<Record> iterator() throws DatabaseException {
        if (sortedTableName == null) {
            sortedTableName = sort();
        }
        return this.transaction.getRecordIterator(sortedTableName);
    }

    private class RecordPairComparator implements Comparator<Pair<Record, Integer>> {
        public int compare(Pair<Record, Integer> o1, Pair<Record, Integer> o2) {
            return SortOperator.this.comparator.compare(o1.getFirst(), o2.getFirst());

        }
    }
    public Run createRun() throws DatabaseException {
        return new Run();
    }
}

