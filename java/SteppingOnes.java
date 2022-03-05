import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// n           1 2 3      4      5        6          7
// init-boards 1 5 137 8825 576451 38177587 2517580016 (distance <=4)
//
// n           1 2 3       4       5         6
// dist-5      1 5 208 21172 2178101 224321747
//             1 1 1.5   2.4     3.8       5.9
//
// initial placement ideas
//
// at least two ones must be at distance <= 2
// each new one must be "reachable" from previous cluster, except if it is its own cluster of 3+
// must discard symmetrical initial boards. could do only the lexicographically first one, if it is
// guaranteed to be visited. may need special handling of the "start".
// verify by print all & sort unique for small n.
//
// distribute the seach across all initial boards. store max for each.
// can store n=6 initials in memory (<33M)
// separate tool for initial boards and searching! simplified distrib.
public class SteppingOnes implements Comparable<SteppingOnes> {
  public static final int C = 1000;

  public static void main(String[] args) {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    if (args.length >= 1 && args[0].equals("sym")) {
      while (true) {
        String line;
        try {
          line = in.readLine();
        } catch (IOException e) {
          e.printStackTrace();
          break;
        }
        if (line == null) {
          break;
        }
        SteppingOnes ones = new SteppingOnes(line);
        ones.isSymmetryMinimal(true);
        ones.printCoords();
      }
      return;
    }

    int n = 4;
    if (args.length > 0) {
      n = Integer.parseInt(args[0]);
    }

    int distance = 4;
    int prevDistance = 4;
    if (args.length > 1) {
      distance = Integer.parseInt(args[1]);
    }
    if (args.length > 2) {
      prevDistance = Integer.parseInt(args[2]);
    }

    boolean only = false;
    if (args.length > 3) {
      only = args[3].equals("only");
    }

    List<SteppingOnes> all = Collections.singletonList(new SteppingOnes(1));
    int prevI = 0;
    for (int k = n < 7 ? 2 : n; k <= n; k++) {
      List<SteppingOnes> next = new ArrayList<>();
      if (n >= 7) {
        String line;
        try {
          line = in.readLine();
        } catch (IOException e) {
          e.printStackTrace();
          break;
        }
        if (line == null) {
          break;
        }
        SteppingOnes ones = new SteppingOnes(line);
        all = Collections.singletonList(ones);
        k--;
      }
      for (SteppingOnes prev : all) {
        // one neighbour < half for each square (strict)
        // which means 1 .. 4 .. 10 .. 22 .. 46 .. 98 .. 190 .. 382 .. 766
        // distance    0    1     2     3     4     5      6      7      8
        // is the maximum number that could be at a certain distance from any 1
        // and the ones could then be at twice that distance?
        // A337663 = 1, 16, 28, 38, 49, 60, >=67, >=74, >=81, ..
        // distance  0   3   4   4   5   5     ?
        List<SteppingOnes> exp = prev.expand(k == 2 ? 2 : distance, prevDistance);
        //System.err.println("from exp " + exp.size());
        exp = exp.stream().distinct().collect(Collectors.toList());
        //System.err.println("to exp " + exp.size());
        next.addAll(exp);
        prevI++;
        if (k >= 6) { // too many
          // cut local duplicates
          next = next.stream().distinct().collect(Collectors.toList());
          for (SteppingOnes board : next) {
            if (only) {
              if (board.maximalJumpDistance() < distance) {
                continue;
              }
            }
            board.printCoords();
          }
          next.clear();
        }
        if (prevI % 10000 == 0 || (prevI == all.size() && all.size() > 10000)) {
          System.err.printf("expand %d/%d %.1f%%\n", prevI, all.size(), 100.0 * prevI / all.size());
        }
      }
      all = next.stream().distinct().collect(Collectors.toList());
      if (all.size() < next.size()) {
        System.err.printf("size %d removed %d from %d to %d\n", k, next.size() - all.size(), next.size(), all.size());
      } else if (all.size() > 0) {
        System.err.printf("size %d found %d\n", k, next.size());
      }
    }
    all = new ArrayList<>(all);
    Collections.sort(all);

    for (SteppingOnes board : all) {
      if (only) {
        if (board.maximalJumpDistance() < distance) {
          continue;
        }
      }
      board.printCoords();
    }
  }

  public SteppingOnes(int n) {
    ones = new int[n];
  }

  public SteppingOnes(String coordLine) {
    String[] parts = coordLine.split(" ");
    int n = Integer.parseInt(parts[0]);
    ones = new int[n];
    for (int i = 0; i < n; i++) {
      int row = Integer.parseInt(parts[2 * i + 1]);
      int col = Integer.parseInt(parts[2 * i + 2]);
      ones[i] = pos(row, col);
    }
  }

  int n;
  int[] ones;

  public static int pos(int row, int col) {
    return row * C + col;
  }
  public static int row(int pos) {
    return pos / C;
  }
  public static int col(int pos) {
    return pos % C;
  }
  public static int dist(int pos1, int pos2) {
    return Math.max(Math.abs(row(pos1) - row(pos2)), Math.abs(col(pos1) - col(pos2)));
  }
  int maxRow() {
    int maxRow = 0;
    for (int pos : ones) {
      maxRow = Math.max(maxRow, row(pos));
    }
    return maxRow;
  }
  int maxCol() {
    int maxCol = 0;
    for (int pos : ones) {
      maxCol = Math.max(maxCol, col(pos));
    }
    return maxCol;
  }
  void minimize() { // move so minimum row and col is zero
    int minRow = maxRow(), minCol = maxCol();
    for (int pos : ones) {
      minRow = Math.min(minRow, row(pos));
      minCol = Math.min(minCol, col(pos));
    }
    for (int i = 0; i < ones.length; i++) {
      int pos = ones[i];
      ones[i] = pos(row(pos) - minRow, col(pos) - minCol);
    }
  }

  public void printCoords() {
    System.out.println(coordsString());
  }

  public void errBoard() {
    int n = ones.length, maxRow = maxRow(), maxCol = maxCol();
    for (int row = 0; row <= maxRow; row++) {
      for (int col = 0; col <= maxCol; col++) {
        int pos = pos(row, col);
        int x = 0;
        for (int one : ones) {
          if (one == pos) {
            x += 1;
          }
        }
        System.err.printf("  %s", x != 0 ? "" + x : ".");
      }
      System.err.println();
    }
  }

  public String coordsString() {
    StringBuilder sb = new StringBuilder();
    sb.append(ones.length);
    for (int pos : ones) {
      sb.append(" " + row(pos) + " " + col(pos));
    }
    return sb.toString();
  }

  public List<SteppingOnes> expand(int distance, int prevDistance) {
    int n = ones.length, maxRow = maxRow(), maxCol = maxCol();
    List<SteppingOnes> list = new ArrayList<>();
    for (int row = -distance; row <= maxRow + distance; row++) {
      for (int col = -distance; col <= maxCol + distance; col++) {
        int dr = -Math.min(0, row), dc = -Math.min(0, col);
        int addPos = pos(row + dr, col + dc);
        SteppingOnes exp = new SteppingOnes(n + 1);
        boolean dup = false, reach = false;
        for (int i = 0; i < ones.length; i++) {
          int pos0 = ones[i];
          int pos = pos(row(pos0) + dr, col(pos0) + dc);
          if (pos == addPos) {
            dup = true;
            break;
          }
          int dist = Math.max(Math.abs(row - row(pos0)), Math.abs(col - col(pos0)));
          if (dist <= distance) {
            reach = true;
          }
          exp.ones[i] = pos;
        }
        if (dup || !reach) {
          continue;
        }
        exp.ones[n] = addPos;
        Arrays.sort(exp.ones);
        boolean smallerPre = false;
        for (SteppingOnes pre : exp.otherPrecursors(addPos)) {
          if (pre.minimalDistance() > 2) {
            continue; // must have seed
          }
          if (pre.maximalJumpDistance() > prevDistance) {
            continue; // can't have gaps
          }
          if (Arrays.compare(pre.ones, this.ones) < 0) {
            smallerPre = true;
          }
        }
        if (smallerPre) {
          //System.err.println("smaller pre " + row + "," + col + " " + dr + "," + dc);
          //exp.errBoard();
          continue;
        }
        if (exp.isSymmetryMinimal(true)) {
          //System.err.println("exp " + row + "," + col);
          //exp.errBoard();
          list.add(exp);
        }
      }
    }
    return list;
  }
  public List<SteppingOnes> otherPrecursors(int lastpos) {
    int n = ones.length;
    List<SteppingOnes> list = new ArrayList<>(n - 1);
    for (int i = 0; i < n; ++i) {
      if (ones[i] != lastpos) {
        SteppingOnes pre = new SteppingOnes(n - 1);
        for (int j = 0; j < n; ++j) {
          if (j < i) {
            pre.ones[j] = ones[j];
          } else if (j > i) {
            pre.ones[j - 1] = ones[j];
          }
        }
        pre.minimize();
        if (pre.isSymmetryMinimal(true)) {
          list.add(pre);
        }
      }
    }
    return list;
  }
  public boolean isSymmetryMinimal(boolean update) {
    boolean minimal = true;
    int n = ones.length, maxRow = maxRow(), maxCol = maxCol();
    SteppingOnes that = new SteppingOnes(n);
    int[] minimalOnes = ones;
    for (int rf = -1; rf <= 1; rf += 2) {
      for (int cf = -1; cf <= 1; cf += 2) {
        for (int rc = -1; rc <= 1; rc += 2) {
          for (int i = 0; i < n; i++) {
            int pos = ones[i];
            int row = row(pos), col = col(pos);
            if (rf < 0) { row = maxRow - row; }
            if (cf < 0) { col = maxCol - col; }
            if (rc < 0) {
              int tmp = row;
              row = col;
              col = tmp;
            }
            that.ones[i] = pos(row, col);
          }
          Arrays.sort(that.ones);
          if (Arrays.compare(minimalOnes, that.ones) > 0) {
            minimal = false;
            minimalOnes = that.ones;
            that = new SteppingOnes(n);
          }
        }
      }
    }
    if (update) {
      ones = minimalOnes;
      minimal = true;
    }
    return minimal;
  }

  public int minimalDistance() {
    int n = ones.length;
    if (n < 2) {
      return 0;
    }
    int minDist = dist(ones[0], ones[1]);
    for (int i = 2; i < n; i++) {
      for (int j = 0; j < i; j++) {
        minDist = Math.min(minDist, dist(ones[i], ones[j]));
      }
    }
    return minDist;
  }

  public int minimalDistanceCount() {
    int n = ones.length;
    int minDist = minimalDistance();
    int count = 0;
    for (int i = 1; i < n; i++) {
      for (int j = 0; j < i; j++) {
        if (dist(ones[i], ones[j]) == minDist) {
          count++;
        }
      }
    }
    return count;
  }

  public int maximalJumpDistance() {
    // symmetric floyd-warshall-like
    int n = ones.length;
    int[][] dist = new int[n][n];
    for (int i = 1; i < n; i++) {
      for (int j = 0; j < i; j++) {
        dist[i][j] = dist(ones[i], ones[j]);
        dist[j][i] = dist[i][j];
      }
    }
    for (int k = 0; k < n; ++k) {
      for (int i = 1; i < n; ++i) {
        for (int j = 0; j < i; ++j) {
          dist[i][j] = Math.min(dist[i][j], Math.max(dist[i][k], dist[k][j]));
          dist[j][i] = dist[i][j];
        }
      }
    }
    int maxDist = 0;
    for (int i = 1; i < n; i++) {
      for (int j = 0; j < i; j++) {
        maxDist = Math.max(maxDist, dist[i][j]);
      }
    }
    return maxDist;
  }

  public int compareTo(SteppingOnes that) {
    if (this.ones.length != that.ones.length) {
      return Integer.compare(this.ones.length, that.ones.length);
    }
    return Arrays.compare(this.ones, that.ones);
  }
  public int hashCode() {
    int x = 0;
    for (int pos : ones) {
      x = 7 * x ^ pos;
    }
    return x;
  }
  public boolean equals(Object o) {
    SteppingOnes that = (SteppingOnes) o;
    return Arrays.equals(this.ones, that.ones);
  }
}
