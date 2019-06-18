package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.RecordIterator;

public class SortMergeOperator extends JoinOperator {
    public SortMergeOperator(QueryOperator leftSource,
                             QueryOperator rightSource,
                             String leftColumnName,
                             String rightColumnName,
                             Database.Transaction transaction) throws QueryPlanException, DatabaseException {
        super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);

        // for HW4
        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
        return new SortMergeIterator();
    }

    public int estimateIOCost() throws QueryPlanException {
        //does nothing
        return 0;
    }

    /**
     * An implementation of Iterator that provides an iterator interface for this operator.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might be a useful reference).
     *
     */
    private class SortMergeIterator extends JoinIterator {
        /**
        * Some member variables are provided for guidance, but there are many possible solutions.
        * You should implement the solution that's best for you, using any member variables you need.
        * You're free to use these member variables, but you're not obligated to.
        */

        private String leftTableName;
        private String rightTableName;
        private RecordIterator leftIterator;
        private RecordIterator rightIterator;
        private Record leftRecord;
        private Record nextRecord;
        private Record rightRecord;
        private boolean marked;

        public SortMergeIterator() throws QueryPlanException, DatabaseException {
            super();
            Database.Transaction transaction = SortMergeOperator.this.getTransaction();

            LeftRecordComparator leftComparator = new LeftRecordComparator();
            RightRecordComparator rightComparator = new RightRecordComparator();

            SortOperator leftOperator = new SortOperator(transaction, this.getLeftTableName(), rightComparator);
            SortOperator rightOperator = new SortOperator(transaction, this.getRightTableName(), leftComparator);

            this.leftTableName = leftOperator.sort();
            this.rightTableName = rightOperator.sort();

            this.leftIterator = SortMergeOperator.this.getRecordIterator(leftTableName);
            this.rightIterator = SortMergeOperator.this.getRecordIterator(rightTableName);

            this.nextRecord = null;
            this.leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
            this.rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;

            this.marked = false;

            try {
                fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }
        }

        private void resetRightRecord() {
            this.rightIterator.reset();
            assert(rightIterator.hasNext());
            rightRecord = rightIterator.next();
            rightIterator.mark();
        }

        private void resetLeftRecord() {
            this.leftIterator.reset();
            assert(leftIterator.hasNext());
            leftRecord = leftIterator.next();
            leftIterator.mark();
        }

        /**
         * Advances the left record
         *
         * The thrown exception means we're done: there is no next record
         * It causes this.fetchNextRecord (the caller) to hand control to its caller.
         * Exceptions can be a way to force the parent to "return" (with simple logic).
         * @throws DatabaseException
         */
        private void nextLeftRecord() throws DatabaseException {
            if (!leftIterator.hasNext()) { leftRecord = null; }
            else { leftRecord = leftIterator.next(); }
        }

        private void nextRightRecord() throws DatabaseException {
            if (!rightIterator.hasNext()) { rightRecord = null; }
            else {rightRecord = rightIterator.next();}
        }
        /**
         * Pre-fetches what will be the next record, and puts it in this.nextRecord.
         * Pre-fetching simplifies the logic of this.hasNext() and this.next()
         * @throws DatabaseException
         */
        private void fetchNextRecord() throws DatabaseException {
            LR_RecordComparator cmp = new LR_RecordComparator();

            if (this.leftRecord == null) { throw new DatabaseException("No new record to fetch"); }
            this.nextRecord = null;
            do {
                if (!this.marked && this.leftRecord == null) {
                    throw new DatabaseException("No new record to fetch");
                } else if (!this.marked){

                    while (this.leftRecord != null && cmp.compare(leftRecord, rightRecord) < 0) {
                        nextLeftRecord();
                    }

                    while (this.rightRecord != null && cmp.compare(leftRecord, rightRecord) > 0) {
                        nextRightRecord();
                    }
                    this.marked = true;
                    this.rightIterator.mark();
                }

                if (this.rightRecord != null && this.leftRecord != null) {
                    if (cmp.compare(leftRecord,rightRecord) == 0) {
                        List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
                        List<DataBox> rightValues = new ArrayList<>(rightRecord.getValues());
                        leftValues.addAll(rightValues);
                        this.nextRecord = new Record(leftValues);
                        this.rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;
                    } else {
                        this.rightIterator.reset();;
                        nextRightRecord();
                        nextLeftRecord();
                        this.marked = false;
                    }
                } else {
                    this.rightIterator.reset();;
                    nextRightRecord();
                    nextLeftRecord();
                    this.marked = false;
                }
            } while (!hasNext());
        }


        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        public boolean hasNext() {
            return this.nextRecord != null;
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */
        public Record next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            Record nextRecord = this.nextRecord;
            try {
                this.fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }
            return nextRecord;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        private class LeftRecordComparator implements Comparator<Record> {
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getLeftColumnIndex()));
            }
        }

        private class RightRecordComparator implements Comparator<Record> {
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }

        /**
        * Left-Right Record comparator
        * o1 : leftRecord
        * o2: rightRecord
        */
        private class LR_RecordComparator implements Comparator<Record> {
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }
    }
}
