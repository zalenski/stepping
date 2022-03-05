import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// https://oeis.org/A337663
//       n = 1,  2,  3,  4,  5,  6,    7,    8,    9, ..
// A337663 = 1, 16, 28, 38, 49, 60, >=67, >=74, >=81, ..
//
// (mathologer, https://www.youtube.com/watch?v=m4Uth-EaTZ8)
// ("Some lower bounds for 7 <= n <= 9.", https://oeis.org/A337663/a337663_1.txt)

public class SteppingStone {
  public static void main(String[] args) {
    long startNanos = System.nanoTime();
    int n = 5;
    boolean inputSearch = false, inputSearchCaching = false;
    if (args.length > 0) {
      if (args[0].equals("in")) {
        inputSearch = true;
        inputSearchCaching = true;
      } else if (args[0].equals("in7")) {
        inputSearch = true;
        return;
      } else {
        n = Integer.parseInt(args[0]);
      }
    }

    InitialPlacementSearch ips = new InitialPlacementSearch(n);
    ips.startNanos = startNanos;

    if (args.length > 1) {
      ips.noSearch = args[1].equals("no");
    }

    int maxFound = inputSearch ? ips.inputSearch(inputSearchCaching) :  ips.search();

    System.out.println(maxFound);
    System.out.printf("%.3fs ", (System.nanoTime() - startNanos) / 1e9);
    System.out.printf("max/board: %d ", ips.maxPositionsPerBoard);
    System.out.printf("initial boards: %d searched positions: %d\n", ips.initialBoards, ips.searchedPositions);
  }
}

class InitialPlacementSearch {
  long startNanos;
  boolean noSearch;

  int side = 100;
  int n;
  int[] positions;
  int maxFound;
  public InitialPlacementSearch(int n) {
    this.n = n;
    positions = new int[n];
  }

  long initialBoards, searchedPositions, maxPositionsPerBoard;
  ExecutorService exec = Executors.newWorkStealingPool();
  Semaphore available = new Semaphore(20, true);

  public int search() {
    search(0);
    exec.shutdown();
    try {
      exec.awaitTermination(1000, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return -1;
    }
    return maxFound;
  }
  public int search(int i) {
    if (i == n) {
      initialBoards++;
      if (initialBoards % 10000 == 0) {
        if (startNanos != 0) {
          System.out.printf("%.3fs ", (System.nanoTime() - startNanos) / 1e9);
        }
        System.out.printf("max/board: %d ", maxPositionsPerBoard);
        System.out.printf("initial boards: %d searched positions: %d\n", initialBoards, searchedPositions);
        if (noSearch) {
          print();
        }
      }
      if (noSearch) {
        return 0;
      }
      Board b = new Board(side, side, 100);
      for (int pos : positions) {
        b.place(pos, 1);
      }
      //b.print();
      //System.out.println(maxFound);
      b.maxFound = maxFound;
      try {
        available.acquire();
      } catch (InterruptedException e) {
        e.printStackTrace();
        return -1;
      }
      exec.execute(() -> {
        b.search(2);
        synchronized (InitialPlacementSearch.this) {
          maxFound = Math.max(maxFound, b.maxFound);
          searchedPositions += b.searchedPositions;
          maxPositionsPerBoard = Math.max(maxPositionsPerBoard, b.searchedPositions);
          available.release();
        }
      });
    } else if (i == 0) {
      positions[0] = 40 * side + 40;
      search(i + 1);
    } else {
      int minCol = 40, maxRow = 40, maxCol = 40;
      for (int j = 0; j < i; j++) {
        maxRow = Math.max(maxRow, positions[j] / side);
        maxCol = Math.max(maxCol, positions[j] % side);
      }
      final int extra = n >= 2 ? 4 : 3; // !!!
      for (int row = maxRow; row <= maxRow + extra; row++) {
        for (int col = (row == maxRow ? positions[i - 1] % side + 1 : minCol); col <= maxCol + extra; col++) {
          positions[i] = row * side + col;
          search(i + 1);
        }
      }
    }
    return maxFound;
  }

  int inputSearch(boolean caching) {
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
      if (positions.length != n) {
        positions = new int[n];
      }
      for (int i = 0; i < n; i++) {
        int pos = ones.ones[i];
        positions[i] = (20 + SteppingOnes.row(pos)) * side + 20 + SteppingOnes.col(pos);
      }
      search(n);
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

class Board {
  public Board(int rows, int cols, int max) {
    this.rows = rows;
    this.cols = cols;
    this.min = 2;
    this.max = max;
    size = rows * cols;
    stride = cols;
    deltas = new int[]{-stride - 1, -stride, -stride + 1, -1, 0, 1, stride - 1, stride, stride + 1};
    board = new int[size];
    counts = new int[size];
    candPrev = new int[size + max + 1];
    candNext = new int[size + max + 1];
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

  int min, max, maxFound;
  long searchedPositions;

  int pos(int row, int col) {
    return row * stride + col;
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
    board[pos] += value;
    for (int delta : deltas) {
      int neigh = pos + delta;
      counts[neigh] += value;
      if (board[neigh] == 0) {
        link(neigh);
      }
    }
    //System.out.printf("place %d,%d %d\n", midRow(pos), midCol(pos), value);
    //print();
  }

  void unlink(int pos) {
    int count = counts[pos];
    if (count < min || count > max) {
      return;
    }
    candNext[candPrev[pos]] = candNext[pos];
    candPrev[candNext[pos]] = candPrev[pos];
  }

  void link(int pos) {
    int count = counts[pos];
    if (count < min || count > max) {
      return;
    }
    candPrev[pos] = size + count;
    candNext[pos] = candNext[size + count];
    candPrev[candNext[pos]] = pos;
    candNext[size + count] = pos;
  }

  void print() {
    print(false);
  }

  void print(boolean withCounts) {
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
          System.out.printf("   ");
        }
      }
      System.out.println();
    }
    /*
    for (int row = minRow - 1; row <= maxRow + 1; row++) {
      for (int col = minCol - 1; col <= maxCol + 1; col++) {
        int pos = pos(row, col);
        System.out.printf("%3d", counts[pos]);
      }
      System.out.println();
    }
    */
    for (int count = 0; count < max; count++) {
      if (candNext[size + count] == size + count) {
        continue;
      }
      if (withCounts) {
        System.out.printf("count %d", count);
      }
      for (int pos = candNext[size + count]; pos < size; pos = candNext[pos]) {
        if (withCounts) {
          System.out.printf(" %d,%d", midRow(pos), midCol(pos));
        }
        // FIXME: always check!
        if (counts[pos] != count && count >= min) {
          System.out.printf("!WRONG_COUNT(%d)", counts[pos]);
        }
      }
      if (withCounts) {
        System.out.println();
      }
    }
  }

  int search(int from) {
    int numCandidates = 0;
    for (int pos = candNext[size + from]; pos < size; pos = candNext[pos]) {
      numCandidates++;
    }
    //System.out.printf("search %d candidates %d\n", from, numCandidates);
    int[] candidates = new int[numCandidates];
    for (int i = 0, pos = candNext[size + from]; pos < size; pos = candNext[pos]) {
      candidates[i++] = pos;
    }
    for (int pos : candidates) {
      maxFound = Math.max(maxFound, from);
      min = 2; //from + 1;
      place(pos, from);
      searchedPositions++;
      if (from == maxFound) {
        print();
        System.out.println(from);
      }
      search(from + 1);
      min = 2; //from + 1;
      place(pos, -from);
      //print();
    }
    return maxFound;
  }
}
