package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.common.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.io.Page;
import edu.berkeley.cs186.database.table.Record;

public class PNLJOperator extends JoinOperator {
    public PNLJOperator(QueryOperator leftSource,
                        QueryOperator rightSource,
                        String leftColumnName,
                        String rightColumnName,
                        Database.Transaction transaction) throws QueryPlanException, DatabaseException {
        super(leftSource,
              rightSource,
              leftColumnName,
              rightColumnName,
              transaction,
              JoinType.PNLJ);

        // for HW4
        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
        return new PNLJIterator();
    }

    public int estimateIOCost() throws QueryPlanException {
        //does nothing
        return 0;
    }

    /**
     * PNLJ: Page Nested Loop Join
     *  See lecture slides.
     *
     * An implementation of Iterator that provides an iterator interface for this operator.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might prove to be a useful reference).
     */
    private class PNLJIterator extends JoinIterator {
        /**
         * Some member variables are provided for guidance, but there are many possible solutions.
         * You should implement the solution that's best for you, using any member variables you need.
         * You're free to use these member variables, but you're not obligated to.
         */

        private Iterator<Page> leftIterator = null;
        private Iterator<Page> rightIterator = null;
        private BacktrackingIterator<Record> leftRecordIterator = null;
        private BacktrackingIterator<Record> rightRecordIterator = null;
        private Record leftRecord = null;
        private Record nextRecord = null;

        private Record rightRecord = null;

        public PNLJIterator() throws QueryPlanException, DatabaseException {
            super();
            this.leftIterator = PNLJOperator.this.getPageIterator(this.getLeftTableName());
            this.rightIterator = PNLJOperator.this.getPageIterator(this.getRightTableName());

            this.leftIterator.next();
            this.rightIterator.next();

            this.leftRecordIterator = PNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftIterator, 1);
            this.rightRecordIterator = PNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);

            this.nextRecord = null;

            this.leftRecord = leftRecordIterator.hasNext() ? leftRecordIterator.next() : null;
            this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;

            // We mark the first record so we can reset to it when we advance the left record.
            if (rightRecord != null) {
                rightRecordIterator.mark();
            } else { return; }

            if (leftRecord != null) {
                leftRecordIterator.mark();
            }

            try {
                fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }
        }

        private void resetRightRecord() {
            this.rightRecordIterator.reset();
            assert(rightRecordIterator.hasNext());
            rightRecord = rightRecordIterator.next();
            rightRecordIterator.mark();
        }

        private void resetLeftRecord() {
            this.leftRecordIterator.reset();
            assert(leftRecordIterator.hasNext());
            leftRecord = leftRecordIterator.next();
            leftRecordIterator.mark();
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
            if (!leftRecordIterator.hasNext()) { leftRecord = null; }
            else { leftRecord = leftRecordIterator.next(); }
        }

        private void nextRightRecord() throws DatabaseException {
            if (!rightRecordIterator.hasNext()) { rightRecord = null; }
            else {rightRecord = rightRecordIterator.next();}
        }
        /**
         * Pre-fetches what will be the next record, and puts it in this.nextRecord.
         * Pre-fetching simplifies the logic of this.hasNext() and this.next()
         * @throws DatabaseException
         */
        private void fetchNextRecord() throws DatabaseException {
            if (this.leftRecord == null) { throw new DatabaseException("No new record to fetch"); }
            this.nextRecord = null;
            do {
                if (this.rightRecord != null) {
                    DataBox leftJoinValue = this.leftRecord.getValues().get(PNLJOperator.this.getLeftColumnIndex());
                    DataBox rightJoinValue = rightRecord.getValues().get(PNLJOperator.this.getRightColumnIndex());
                    if (leftJoinValue.equals(rightJoinValue)) {
                        List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
                        List<DataBox> rightValues = new ArrayList<>(rightRecord.getValues());
                        leftValues.addAll(rightValues);
                        this.nextRecord = new Record(leftValues);
                    }
                    this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;
                } else if (leftRecordIterator.hasNext()) {
                    nextLeftRecord();
                    resetRightRecord();
                } else if (rightIterator.hasNext()) {
                    this.rightRecordIterator = PNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);
                    nextRightRecord();
                    rightRecordIterator.mark();
                    resetLeftRecord();
                } else if (leftIterator.hasNext()) {
                    this.leftRecordIterator = PNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftIterator, 1);
                    nextLeftRecord();
                    this.leftRecordIterator.mark();

                    this.rightIterator = PNLJOperator.this.getPageIterator(this.getRightTableName());
                    this.rightIterator.next();
                    this.rightRecordIterator = PNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);
                    nextRightRecord();
                    this.rightRecordIterator.mark();
                } else {
                    throw new DatabaseException("No new record to fetch");
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
    }
}

