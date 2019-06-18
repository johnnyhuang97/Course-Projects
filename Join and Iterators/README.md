# Iterators and Join Algorithms

## Overview
In this project, I will implement iterators and join algorithms over tables in Java. In this
document, we explain

- how to fetch the release code from GitHub (everything should be similar to the steps you have done for HW2),
- how to program in Java on the virtual machine, and
- what code you have to implement.

## Directory Guide

### common
The `common` directory now contains an interface called a `BacktrackingIterator`. Iterators that implement this will be able to mark a point during iteration, and reset back to that mark. For example, here we have a back tracking iterator that just returns 1, 2, and 3, but can backtrack:

```java
BackTrackingIterator<Integer> iter = new BackTrackingIteratorImplementation();
iter.next(); //returns 1
iter.next(); //returns 2
iter.mark();
iter.next(); //returns 3
iter.hasNext(); //returns false
iter.reset();
iter.hasNext(); // returns true
iter.next(); //returns 2

```
`ArrayBacktrackingIterator` implements this interface. It takes in an array and returns a backtracking iterator over the values in that array.

### Table
The `table` directory now contains an implementation of
relational tables that store values of type `DataBox`. The `RecordId` class uniquely identifies a record on a page by its page number and entry number on that page. A `Record` is represented as a list of DataBoxes. A `Schema` is represented as list of column names and a list of column types. A `RecordIterator` takes in an iterator over `RecordId`s for a given table and returns an iterator over the corresponding records. A `Table` is made up of pages, with the first page always being the header page for the file. See the comments in `Table` for how the data of a table is serialized to a file.

### Database
The `Database` class represents a database. It is the interface through which we can create and update tables, and run queries on tables. When a user is operating on the database, they start a `transaction`, which allows for atomic access to tables in the database. You should be familiar with the code in here as it will be helpful when writing your own tests.

### Query
The `query` directory contains what are called query operators. These are operators that are applied to one or more tables, or other operators. They carry out their operation on their input operator(s) and return iterators over records that are the result of applying that specific operator. We call them “operators” here to distinguish them from the Java iterators you will be implementing.

`SortOperator` does the external merge sort algorithm covered in lecture. It contains a subclass called a `Run`. A `Run` is just an object that we can add records to, and read records from. Its underlying structure is a Table.

`JoinOperator` is the base class that join operators you will implement extend. It contains any methods you might need to deal with tables through the current running transaction. This means you should not deal directly with `Table` objects in the `Query` directory, but only through methods given through the current transaction.



## Implementing Iterators and Join Algorithms


#### Notes Before You Begin
 In lecture, we sometimes use the words `block` and `page` interchangeably to describe a single unit of transfer from disc. The notion of a `block` when discussing join algorithms is different however. A `page` is a single unit of transfer from disc, and a  `block` is one or more `pages`. All uses of `block` in this project refer to this alternate definition.

 Besides when the comments tell you that you can do something in memory, everything else should be **streamed**. You should not hold more pages in memory at once than the given algorithm says you are allowed to.

  Remember the test cases we give you are not comprehensive, so you should write your own tests to further test your code and catch edge cases. Also, we give you all the tests for the current state of the database, but we skip some of them for time.

  The tests we provide to you for this HW are under `TestTable` for part 1, `TestJoinOperator` for parts 2 and 4, and `TestSortOperator` for part 3. If you are running tests from the terminal (and not an IDE), you can pass `-Dtest=TestName` to `mvn test` to only run a single file of tests.

#### 1. Table Iterators

In the `table` directory, fill in the classes `Table#RIDPageIterator` and `Table#RIDBlockIterator`. The tests in `TestTable` should pass once this is complete.

*Note on testing*: If you wish to write your own tests on `Table#RIDBlockIterator`, be careful with using the `Iterator<Page> block, int maxPages` constructor: you have to get a new `Iterator<Page>` if you want to recreate the iterator in the same test.

#### 2. Nested Loops Joins

Move to the `query` directory. You may first want to take a look at `SNLJOperator`. Complete `PNLJOperator` and `BNLJOperator`. The PNLJ and BNLJ tests in `TestJoinOperator` should pass once this is complete.

#### 3: External Sort

Complete implementing `SortOperator.java`. The tests in `TestSortOperator` should pass once this is complete.

In the hidden tests, we may test the methods independently by replacing other methods with the staff solution, so make sure they each function exactly as described in the comments. This also allows for partial credit should one of your methods not work correctly.

#### 4: Sort Merge Join

Complete implementing `SortMergeOperator.java`. The sort phase of this join should use your previously implemented `SortOperator#sort` method. Note that we do not do the optimization discussed in lecture where the join happens during the last pass of sorting the two tables. We keep the sort phase completely separate from the join phase. The SortMerge tests in `TestJoinOperator` should pass once this is complete.


[eclipse_maven]: https://stackoverflow.com/a/36242422
[intellij_maven]: https://www.jetbrains.com/help/idea//2017.1/importing-project-from-maven-model.html
[eclipse_debugging]: http://www.vogella.com/tutorials/EclipseDebugging/article.html
[intellij_debugging]: https://www.jetbrains.com/help/idea/debugging.html

