import sys


def count(board, x):
    s = 0
    for row in board:
        s += row.count(x)
    return s


def square(board, r, c):
    if 0 <= r < len(board):
        row = board[r]
        if 0 <= c < len(row):
            return row[c]
    return 0


def check(board, x):
    for r, row in enumerate(board):
        for c, item in enumerate(row):
            if item == x:
                s = 0
                l = []
                for dr in [-1, 0, 1]:
                    for dc in [-1, 0, 1]:
                        neigh = square(board, r + dr, c + dc)
                        if 0 < neigh < x:
                            s += neigh
                            l.append(neigh)
                print('%s = %d' % (' + '.join(map(str, l)), sum(l)))
                return s == x
    return False


def verify():
    board = []
    seenline = False
    for line in sys.stdin:
        seenline = True
        if ' . ' not in line:
            if board:
                break
            else:
                return False
        row = []
        for item in line.rstrip().split():
            row.append(0 if item == '.' else int(item))
        board.append(row)
    if not seenline:
        return None

    n = count(board, 1)

    for row in board:
        print(''.join('%3d' % item if item else '  .' for item in row))

    i = 1
    while True:
        if count(board, i + 1) == 1:
            i += 1
        else:
            break
        if not check(board, i):
            print('check failed at', i)
            return

    if max(max(row) for row in board) != i:
        print('check failed, gap after', i)
        return

    print('a(%d) >= %d' % (n, i))
    return (n, i)


def main():
    while True:
        r = verify()
        if r is None:
            break


if __name__ == '__main__':
    main()
