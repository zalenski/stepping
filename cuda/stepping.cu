#include <iostream>
#include <vector>
#include "cuda.h"

using namespace std;

typedef vector<int> Vi;

const int S = 50, S2 = S * S;
const int M = 100;
const bool VERIFY = false;

// https://oeis.org/A337663
//       n = 1,  2,  3,  4,  5,  6,    7,    8,    9, ..
// A337663 = 1, 16, 28, 38, 49, 60, >=67, >=74, >=81, ..
//
// (mathologer, https://www.youtube.com/watch?v=m4Uth-EaTZ8)
// ("Some lower bounds for 7 <= n <= 9.", https://oeis.org/A337663/a337663_1.txt)

// n           1 2 3      4      5        6          7
// init-boards 1 5 137 8825 576451 38177587 2517580016 (distance <=4)

long long INIT_BOARDS[] = {
  // n
  // 1  2    3     4       5         6           7
  1, 1, 5, 137, 8825, 576451, 38177587, 2517580016L,
};
int PRINT_THRES[] = {
  // n
  // 1   2   3   4   5   6     7     8     9
  // A337663
  // 1, 16, 28, 38, 49, 60, >=67, >=74, >=81, ..
  0, 1, 16, 28, 38, 47, 57,   61,   64,   74,
};

struct board {
  int delta[8];

  uint16_t board[S2];
  uint16_t counts[S2];

  uint16_t candNext[S2 + M + 1];
  uint16_t candPrev[S2 + M + 1];
  uint16_t candList[S2];
  int candListPtr, candLevel;
  uint16_t candPtr[M + 1];

  int maxFound;
  uint16_t best[S2];
  long long searchedPositions;

  __device__
  void init();
  __device__
  void place(int pos, int value);
  __device__
  void unlink(int pos);
  __device__
  void link(int pos);

  void print(bool best = false);
  void printOnes();
  void verify();

  __device__
  bool search1();
};

int Pos(int row, int col) {
  return row * S + col;
}
int posRow(int pos) {
  return pos / S;
}
int posCol(int pos) {
  return pos % S;
}

__device__
void board::init() {
  delta[0] = -S - 1;
  delta[1] = -S;
  delta[2] = -S + 1;
  delta[3] = -1;
  delta[4] = 1;
  delta[5] = S - 1;
  delta[6] = S;
  delta[7] = S + 1;
  for (int i = 0; i < S2; i++) {
    board[i] = 0;
    counts[i] = 0;
    candPrev[i] = 0;
    candNext[i] = 0;
    candList[i] = 0;
    best[i] = 0;
  }
  for (int i = 0; i <= M; i++) {
    candPrev[S2 + i] = S2 + i;
    candNext[S2 + i] = S2 + i;
    candPtr[i] = 0;
  }
  candListPtr = 0;
  candLevel = 2;
  maxFound = 0;
  searchedPositions = 0;
}

__device__
void board::place(int pos, int value) {
  // cout << "place " << posRow(pos)-20 << ',' << posCol(pos)-20 << ' ' << value << endl;
  for (int i = 0; i < 8; i++) {
    int neigh = pos + delta[i];
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
  for (int i = 0; i < 8; i++) {
    int neigh = pos + delta[i];
    counts[neigh] += value;
    if (board[neigh] == 0) {
      link(neigh);
    }
  }
  if (board[pos] > maxFound) {
    maxFound = board[pos];
    for (int i = 0; i < S2; i++) {
      best[i] = board[i];
    }
  }
}

__device__
void board::unlink(int pos) {
  int count = counts[pos];
  if (count > M) {
    return;
  }
  candNext[candPrev[pos]] = candNext[pos];
  candPrev[candNext[pos]] = candPrev[pos];
}

__device__
void board::link(int pos) {
  int count = counts[pos];
  if (count > M) {
    return;
  }
  candPrev[pos] = S2 + count;
  candNext[pos] = candNext[S2 + count];
  candPrev[candNext[pos]] = pos;
  candNext[S2 + count] = pos;
}

void board::print(bool best) {
  if (VERIFY && !best) {
    verify();
  }
  uint16_t *board = best ? this->best : this->board;
  int minRow = S, minCol = S, maxRow = 0, maxCol = 0;
  for (int row = 0; row < S; row++) {
    for (int col = 0; col < S; col++) {
      int pos = Pos(row, col);
      if (board[pos] > 0) {
        minRow = min(minRow, row);
        minCol = min(minCol, col);
        maxRow = max(maxRow, row);
        maxCol = max(maxCol, col);
      }
    }
  }
  for (int row = minRow; row <= maxRow; row++) {
    for (int col = minCol; col <= maxCol; col++) {
      int pos = Pos(row, col);
      if (board[pos] > 0) {
        cout.width(3);
        cout << board[pos];
      } else {
        cout << "  .";
      }
    }
    cout << endl;
  }
}

void board::printOnes() {
  if (VERIFY) {
    verify();
  }
  uint16_t *board = this->board;
  int minRow = S, minCol = S, maxRow = 0, maxCol = 0;
  int n = 0;
  for (int row = 0; row < S; row++) {
    for (int col = 0; col < S; col++) {
      int pos = Pos(row, col);
      if (board[pos] == 1) {
        minRow = min(minRow, row);
        minCol = min(minCol, col);
        maxRow = max(maxRow, row);
        maxCol = max(maxCol, col);
        n++;
      }
    }
  }
  cout << n;
  for (int row = minRow; row <= maxRow; row++) {
    for (int col = minCol; col <= maxCol; col++) {
      int pos = Pos(row, col);
      if (board[pos] == 1) {
        cout << ' ' << (row - minRow) << ' ' << (col - minCol);
      }
    }
  }
  cout << endl;
}

// verify() function, to check all counts, candidate lists, and placements on board!
void board::verify() {
  int minRow = S, minCol = S, maxRow = 0, maxCol = 0;
  for (int row = 0; row < S; row++) {
    for (int col = 0; col < S; col++) {
      int pos = Pos(row, col);
      if (board[pos] > 0) {
        minRow = min(minRow, row);
        minCol = min(minCol, col);
        maxRow = max(maxRow, row);
        maxCol = max(maxCol, col);
      }
    }
  }
  for (int row = minRow - 1; row <= maxRow + 1; row++) {
    for (int col = minCol - 1; col <= maxCol + 1; col++) {
      int pos = Pos(row, col);
      if (board[pos] == 0) {
        int count = 0;
        for (int i = 0; i < 8; i++) {
          count += board[pos + delta[i]];
        }
        if (counts[pos] != count) {
          print();
          printf("count mismatch %d != %d at %d,%d\n", counts[pos], count, row - minRow, col - minCol);
          throw "count mismatch";
        }
        if (count >= 2 && count <= M) {
          bool found = false;
          for (int candPos = candNext[S2 + count]; candPos < S2; candPos = candNext[candPos]) {
            if (candPos == pos) {
              found = true;
              break;
            }
          }
          if (!found) {
            print();
            printf("count not found among candidates for %d at %d,%d\n", count, row - minRow, col - minCol);
            for (int candPos = candNext[S2 + count]; candPos < S2; candPos = candNext[candPos]) {
              printf(" cand %d,%d", posRow(candPos) - minRow, posCol(candPos) - minCol);
            }
            printf("\n");
            throw "candidate missing mismatch";
          }
        }
      }
    }
  }
  for (int count = 2; count <= M; count++) {
    for (int pos = candNext[S2 + count]; pos != S2 + count; pos = candNext[pos]) {
      if (board[pos] != 0) {
        print();
        printf("placed %d in candidates for %d at %d,%d", board[pos], count, posRow(pos) - minRow, posCol(pos) - minCol);
        for (int candPos = candNext[S2 + count]; candPos < S2; candPos = candNext[candPos]) {
          printf(" cand %d,%d", posRow(candPos) - minRow, posCol(candPos) - minCol);
        }
        printf("\n");
        throw "candidate already placed mismatch";
      }
      if (board[pos] == 0 && counts[pos] != count && count >= 2) {
        print();
        printf("wrong count %d among candidates for %d at %d,%d", counts[pos], count, posRow(pos) - minRow, posCol(pos) - minCol);
        for (int candPos = candNext[S2 + count]; candPos < S2; candPos = candNext[candPos]) {
          printf(" cand %d,%d", posRow(candPos) - minRow, posCol(candPos) - minCol);
        }
        printf("\n");
        throw "candidate wrong count mismatch";
      }
    }
  }
}

__device__
bool board::search1() {
  //if (VERIFY) {
  //  verify();
  //}
  //while (candLevel >= 2) {
  candList[candListPtr++] = S2;
  for (int pos = candNext[S2 + candLevel]; pos < S2; pos = candNext[pos]) {
    candList[candListPtr++] = pos;
  }
  candPtr[candLevel] = candListPtr;

  int pos = candList[--candPtr[candLevel]];
  while (pos >= S2) {
    candLevel--;
    if (candLevel < 2) {
      return false;
    }
    pos = candList[candPtr[candLevel]];
    place(pos, -candLevel);
    candListPtr = candPtr[candLevel];
    pos = candList[--candPtr[candLevel]];
  }
  place(pos, candLevel);
  searchedPositions++;
  candLevel++;
  //}
  return true;
}

long long hist[M];
long long histSearched[M];
long long maxSearched[M];
void print_hist(int maxFound) {
  cout << maxFound << " hist";
  for (int i = 2; i <= maxFound; i++) {
    cout << ' ' << hist[i];
  }
  cout << endl;
  cout << maxFound << " avg";
  for (int i = 2; i <= maxFound; i++) {
    cout << ' ' << histSearched[i] / (hist[i] != 0 ? hist[i] : 1);
  }
  cout << endl;
  cout << maxFound << " max";
  for (int i = 2; i <= maxFound; i++) {
    cout << ' ' << maxSearched[i];
  }
  cout << endl;
}

time_t t0 = time(0);
int maxFound = 0;
long long initBoards = 0, searchedPositions = 0;
int n;

int read_boards(int max_boards, int *&boards, int *&results) {
  int board_n = 0;
  for (int board_i = 0; board_i < max_boards; board_i++) {
    if (!(cin >> n)) {
      break;
    }
    if (boards == NULL) {
      cudaMallocManaged(&boards, n * max_boards * sizeof(int));
      cudaMallocManaged(&results, 2 * max_boards * sizeof(int));
      for (int i = 0; i < n * max_boards; i++) {
        boards[i] = 0;
      }
      for (int i = 0; i < 2 * max_boards; i++) {
        results[i] = 0;
      }
    }
    for (int i = 0; i < n; i++) {
      int row, col;
      cin >> row >> col;
      int pos = Pos(row + 20, col + 20);
      boards[board_i * n + i] = pos;
    }
    results[board_i * 2] = 0;
    results[board_i * 2 + 1] = 0;
    board_n = board_i + 1;

    initBoards++;
    if (initBoards % 10000 == 0) {
      if (initBoards % 100000 == 0) {
        print_hist(maxFound);
      }
      double runSeconds = time(0) - t0;
      double totalHours = runSeconds * INIT_BOARDS[n] / initBoards / 3600;
      double etaHours = totalHours - runSeconds / 3600;
      printf("%d ", maxFound);
      printf("%.0fs tot %.1fh eta %.1fh ", runSeconds, totalHours, etaHours);
      printf("initial boards: %lld/%lld searched positions: %lld\n",
          initBoards, INIT_BOARDS[n], searchedPositions);
    }
  }
  return board_n;
}

__global__
void stepping_main(int board_n, int n, int *boards, int *results) {
  int index = blockIdx.x * blockDim.x + threadIdx.x;
  int stride = blockDim.x * gridDim.x;
  board b;
  int board_i = index;
  bool new_board = true;
  while (board_i < board_n) {
    if (new_board) {
      b.init();
      for (int i = 0; i < n; i++) {
        b.place(boards[board_i * n + i], 1);
      }
    }
    new_board = false;
    if (!b.search1()) {
      results[board_i * 2] = b.maxFound;
      results[board_i * 2 + 1] = b.searchedPositions;
      new_board = true;
      board_i += stride;
    }
  }
}

void collect_results(int board_n, int *boards, int *results) {
  for (int board_i = 0; board_i < board_n; board_i++) {
    int biMaxFound = results[board_i * 2];
    int biSearchedPositions = results[board_i * 2 + 1];
    hist[biMaxFound]++;
    histSearched[biMaxFound] += biSearchedPositions;
    if (biSearchedPositions > maxSearched[biMaxFound]) {
      maxSearched[biMaxFound] = biSearchedPositions;
    }
    searchedPositions += biSearchedPositions;
    if (biMaxFound > maxFound) {
      maxFound = biMaxFound;
    }
    if (biMaxFound >= PRINT_THRES[n]) {
      cout << biMaxFound << " max " << n;
      for (int i = 0; i < n; i++) {
        int pos = boards[board_i * n + i];
        int row = posRow(pos) - 20, col = posCol(pos) - 20;
        cout << ' ' << row << ' ' << col;
      }
      cout << endl;
      // TODO: capture best board!
    }
  }
}

void stepping_end() {
  {
    print_hist(maxFound);
    double runSeconds = time(0) - t0;
    double totalHours = runSeconds / 3600;
    printf("%d ", maxFound);
    printf("%.0fs tot %.1fh ", runSeconds, totalHours);
    printf("initial boards: %lld/%lld searched positions: %lld\n",
        initBoards, INIT_BOARDS[n], searchedPositions);
  }
}

#ifndef STEPPING_THREAD
int main() {
  int *boards = 0, *results = 0;
  while (true) {
    const int AT_A_TIME = 1024 * 256 + 176 * 256; //10240;
    int board_n = read_boards(AT_A_TIME, boards, results);
    if (board_n == 0) {
      break;
    }

printf("pre-sync1\n");
    cudaDeviceSynchronize();
printf("post-sync1\n");
    int blockSize = 256;
    int numBlocks = (board_n + blockSize - 1) / blockSize;
    if (numBlocks > 16) {
      numBlocks = 16;
    }
    //stepping_main<<<1, 1>>>(board_n, n, boards, results);
    stepping_main<<<numBlocks, blockSize>>>(board_n, n, boards, results);
printf("pre-sync2\n");
    cudaDeviceSynchronize();
printf("post-sync2\n");

    printf("boards %p results %p results[0] %d,%d,%d\n", boards, results, results[0], results[1], results[2]);
    collect_results(board_n, boards, results);
  }
  stepping_end();
  //cudaProfilerStop();
  //cuProfilerStop();
  return 0;
}
#endif
