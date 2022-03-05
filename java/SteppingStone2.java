import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// https://oeis.org/A337663
//       n = 1,  2,  3,  4,  5,  6,   7,    8,    9, ..
// A337663 = 1, 16, 28, 38, 49, 60, >67, >=74, >=81, ..
//
// (mathologer, https://www.youtube.com/watch?v=m4Uth-EaTZ8)
// ("Some lower bounds for 7 <= n <= 9.", https://oeis.org/A337663/a337663_1.txt)

public class SteppingStone2 {
  public static void main(String[] args) {
    long startNanos = System.nanoTime();

    InitialPlacement2 ip = new InitialPlacement2();
    ip.startNanos = startNanos;

    int maxFound = ip.inputSearch();

    System.out.println(maxFound);
    System.out.printf("%d ", maxFound);
    System.out.printf("%.3fs ", (System.nanoTime() - startNanos) / 1e9);
    System.out.printf("max/board: %d ", ip.maxPositionsPerBoard);
    System.out.printf("initial boards: %d searched positions: %d\n", ip.initialBoards, ip.searchedPositions);
  }
}

class InitialPlacement2 {
  long startNanos;

  int side = 100;
  int n;
  int[] positions;
  int maxFound;
  public InitialPlacement2() {
  }

  static final long[] INIT_BOARDS = new long[] {
    // n
    // 1  2    3     4       5         6           7
    1, 1, 5, 137, 8825, 576451, 38177587, 2517580016L,
  };
  static final int[] PRINT_THRES = new int[] {
    // n
    // 1   2   3   4   5   6     7     8     9
    // A337663
    // 1, 16, 28, 38, 49, 60, >=67, >=74, >=81, ..
    0, 1, 16, 28, 38, 48, 57,   61,   64,   74,
  };

  long initialBoards, searchedPositions, maxPositionsPerBoard;
  ExecutorService exec = Executors.newWorkStealingPool();
  Semaphore available = new Semaphore(Runtime.getRuntime().availableProcessors() * 2, true);

  public int search(SteppingOnes ones) {
    initialBoards++;
    if (initialBoards % 10000 == 0) {
      System.out.printf("%d ", maxFound);
      if (startNanos != 0) {
        double runSeconds = (System.nanoTime() - startNanos) / 1e9;
        double totalHours = runSeconds * INIT_BOARDS[n] / initialBoards / 3600;
        double etaHours = totalHours - runSeconds / 3600;
        System.out.printf("%.3fs tot %.1fh eta %.1fh ", runSeconds, totalHours, etaHours);
      }
      if (initialBoards % 100000 == 0) {
        System.out.printf("max/board: %d ", maxPositionsPerBoard);
      }
      System.out.printf("initial boards: %d/%d searched positions: %d\n", initialBoards, INIT_BOARDS[n], searchedPositions);
    }
    Board2 b = new Board2(side, side, 100);
    for (int pos : positions) {
      b.place(pos, 1);
    }
    try {
      available.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
      return -1;
    }
    int nn = n;
    exec.execute(() -> {
      b.search(2);
      synchronized (InitialPlacement2.this) {
        maxFound = Math.max(maxFound, b.maxFound);
        searchedPositions += b.searchedPositions;
        maxPositionsPerBoard = Math.max(maxPositionsPerBoard, b.searchedPositions);
        if (b.maxFound >= PRINT_THRES[nn]) {
          b.bestBoard.print();
          System.out.println(b.maxFound);
          System.out.printf("%d max %s\n", b.maxFound, ones.coordsString());
        }
        //System.out.printf("%d max %s\n", b.maxFound, ones.coordsString());
        available.release();
      }
    });
    return maxFound;
  }

  int inputSearch() {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      String line;
      try {
        line = in.readLine();
      } catch (IOException e) {
        e.printStackTrace();
        return -1;
      }
      if (line == null) {
        break;
      }
      SteppingOnes ones = new SteppingOnes(line);
      //ones.printCoords();
      n = ones.ones.length;
      if (positions == null || positions.length != n) {
        positions = new int[n];
      }
      for (int i = 0; i < n; i++) {
        int pos = ones.ones[i];
        positions[i] = (20 + SteppingOnes.row(pos)) * side + 20 + SteppingOnes.col(pos);
      }
      search(ones);
      // TODO: print line + max for line!
    }
    exec.shutdown();
    try {
      exec.awaitTermination(1000, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return -1;
    }
    return maxFound;
  }

  void print() {
    int minRow = 40, minCol = 40, maxRow = 40, maxCol = 40;
    for (int i = 0; i < n; i++) {
      maxRow = Math.max(maxRow, positions[i] / side);
      maxCol = Math.max(maxCol, positions[i] % side);
    }
    for (int row = minRow; row <= maxRow; row++) {
      for (int col = minCol; col <= maxCol; col++) {
        boolean x = false;
        for (int i = 0; i < n; i++) {
          if (row == positions[i] / side && col == positions[i] % side) {
            x = true;
          }
        }
        System.out.printf(x ? "  1" : "  .");
      }
      System.out.println();
    }
  }
}

class Board2 {
  public static boolean VERIFY = false;
  public Board2(int rows, int cols, int max) {
    this.rows = rows;
    this.cols = cols;
    this.min = 2;
    this.max = max;
    size = rows * cols;
    stride = cols;
    deltas = new int[]{-stride - 1, -stride, -stride + 1, -1, 1, stride - 1, stride, stride + 1};
    board = new int[size];
    counts = new int[size];
    candPrev = new int[size + max + 1];
    candNext = new int[size + max + 1];
    candList = new int[size]; // TODO: figure out max candList size?
    for (int i = 0; i <= max; i++) {
      candPrev[size + i] = size + i;
      candNext[size + i] = size + i;
    }
  }

  int rows, cols, size, stride;
  int[] deltas;
  int[] board;
  int[] counts;

  int[] candPrev;
  int[] candNext;

  int[] candList;
  int candListPtr;

  int min, max, maxFound;
  long searchedPositions;
  Board2 bestBoard;

  int pos(int row, int col) {
    return row * stride + col;
  }
  public int row(int pos) {
    return pos / stride;
  }
  public int col(int pos) {
    return pos % stride;
  }

  int midPos(int dr, int dc) {
    return pos(rows / 2 + dr, cols / 2 + dc);
  }
  int midRow(int pos) {
    return pos / stride - rows / 2;
  }
  int midCol(int pos) {
    return pos % stride - cols / 2;
  }

  // place value at pos, use negative value to remove
  void place(int pos, int value) {
    for (int delta : deltas) {
      int neigh = pos + delta;
      if (board[neigh] == 0) {
        unlink(neigh);
      }
    }
    if (board[pos] == 0) {
      unlink(pos);
    }
    board[pos] += value;
    if (board[pos] == 0) {
      link(pos);
    }
    for (int delta : deltas) {
      int neigh = pos + delta;
      counts[neigh] += value;
      if (board[neigh] == 0) {
        link(neigh);
      }
    }
    //System.out.printf("place %d,%d %d\n", midRow(pos), midCol(pos), value);
    //print();
    if (board[pos] > maxFound) {
      maxFound = Math.max(maxFound, board[pos]);
      bestBoard = new Board2(rows, cols, max);
      bestBoard.board = Arrays.copyOf(board, board.length);
      bestBoard.inVerify = true;
      //bestBoard.print();
      //System.out.println(board[pos]);
    }
  }

  void unlink(int pos) {
    int count = counts[pos];
    if (count > max) {
      return;
    }
    candNext[candPrev[pos]] = candNext[pos];
    candPrev[candNext[pos]] = candPrev[pos];
  }

  void link(int pos) {
    int count = counts[pos];
    if (count > max) {
      return;
    }
    candPrev[pos] = size + count;
    candNext[pos] = candNext[size + count];
    candPrev[candNext[pos]] = pos;
    candNext[size + count] = pos;
  }

  void print() {
    if (VERIFY && !inVerify) {
      verify();
    }
    int minRow = rows, minCol = cols, maxRow = 0, maxCol = 0;
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        int pos = pos(row, col);
        if (board[pos] > 0) {
          minRow = Math.min(minRow, row);
          minCol = Math.min(minCol, col);
          maxRow = Math.max(maxRow, row);
          maxCol = Math.max(maxCol, col);
        }
      }
    }
    for (int row = minRow; row <= maxRow; row++) {
      for (int col = minCol; col <= maxCol; col++) {
        int pos = pos(row, col);
        if (board[pos] > 0) {
          System.out.printf("%3d", board[pos]);
        } else {
          System.out.printf("  .");
        }
      }
      System.out.println();
    }
  }

  // verify() function, to check all counts, candidate lists, and placements on board!
  boolean inVerify;
  void verify() {
    inVerify = true;
    int minRow = rows, minCol = cols, maxRow = 0, maxCol = 0;
    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        int pos = pos(row, col);
        if (board[pos] > 0) {
          minRow = Math.min(minRow, row);
          minCol = Math.min(minCol, col);
          maxRow = Math.max(maxRow, row);
          maxCol = Math.max(maxCol, col);
        }
      }
    }
    for (int row = minRow - 1; row <= maxRow + 1; row++) {
      for (int col = minCol - 1; col <= maxCol + 1; col++) {
        int pos = pos(row, col);
        if (board[pos] == 0) {
          int count = 0;
          for (int delta : deltas) {
            count += board[pos + delta];
          }
          if (counts[pos] != count) {
            print();
            System.out.printf("count mismatch %d != %d at %d,%d\n", counts[pos], count, row - minRow, col - minCol);
            throw new RuntimeException("count mismatch");
          }
          if (count >= min && count <= max) {
            boolean found = false;
            for (int candPos = candNext[size + count]; candPos < size; candPos = candNext[candPos]) {
              if (candPos == pos) {
                found = true;
                break;
              }
            }
            if (!found) {
              print();
              System.out.printf("count not found among candidates for %d at %d,%d\n", count, row - minRow, col - minCol);
              for (int candPos = candNext[size + count]; candPos < size; candPos = candNext[candPos]) {
                System.out.printf(" cand %d,%d", row(candPos) - minRow, col(candPos) - minCol);
              }
              System.out.println();
              throw new RuntimeException("candidate missing mismatch");
            }
          }
        }
      }
    }
    for (int count = min; count < max; count++) {
      for (int pos = candNext[size + count]; pos != size + count; pos = candNext[pos]) {
        if (board[pos] != 0) {
          print();
          System.out.printf("placed %d in candidates for %d at %d,%d", board[pos], count, row(pos) - minRow, col(pos) - minCol);
          for (int candPos = candNext[size + count]; candPos < size; candPos = candNext[candPos]) {
            System.out.printf(" cand %d,%d", row(candPos) - minRow, col(candPos) - minCol);
          }
          System.out.println();
          throw new RuntimeException("candidate already placed mismatch");

        }
        if (board[pos] == 0 && counts[pos] != count && count >= min) {
          print();
          System.out.printf("wrong count %d among candidates for %d at %d,%d", counts[pos], count, row(pos) - minRow, col(pos) - minCol);
          for (int candPos = candNext[size + count]; candPos < size; candPos = candNext[candPos]) {
            System.out.printf(" cand %d,%d", row(candPos) - minRow, col(candPos) - minCol);
          }
          System.out.println();
          throw new RuntimeException("candidate wrong count mismatch");
        }
      }
    }
    inVerify = false;
  }

  int search(int from) {
    if (VERIFY) {
      verify();
    }
    int candListBegin = candListPtr;
    for (int pos = candNext[size + from]; pos < size; pos = candNext[pos]) {
      candList[candListPtr++] = pos;
    }
    int candListEnd = candListPtr;
    //System.out.printf("search %d candidates %d\n", from, candListEnd - candListBegin);
    for (int candListI = candListBegin; candListI < candListEnd; candListI++) {
      int pos = candList[candListI];
      min = 2; //from + 1;
      place(pos, from);
      searchedPositions++;
      search(from + 1);
      min = 2; //from + 1;
      place(pos, -from);
    }
    candListPtr = candListBegin;
    return maxFound;
  }
}
