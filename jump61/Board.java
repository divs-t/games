
package jump61;

import java.util.ArrayDeque;
import java.util.Formatter;
import java.util.ArrayList;

import java.util.function.Consumer;

import static jump61.Side.*;
import static jump61.Square.square;

/** Represents the state of a Jump61 game.  Squares are indexed either by
 *  row and column (between 1 and size()), or by square number, numbering
 *  squares by rows, with squares in row 1 numbered from 0 to size()-1, in
 *  row 2 numbered from size() to 2*size() - 1, etc. (i.e., row-major order).
 *
 *  A Board may be given a notifier---a Consumer<Board> whose
 *  .accept method is called whenever the Board's contents are changed.
 *
 *  @author Divya Sivanandan
 */
class Board {

    /** An uninitialized Board.  Only for use by subtypes. */
    protected Board() {
        _notifier = NOP;
    }

    /** An N x N board in initial configuration. */
    Board(int N) {
        this();
        _myBoard = new Square[N][N];
        _boardStates = new ArrayList<>();
        _numMoves = 0;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                _myBoard[r][c] = square(WHITE, 0);
            }
        }
        _readonlyBoard =  new ConstantBoard(this);
        _boardStates.add(new GameState());
        _boardStates.get(0).saveState();
    }

    /** A board whose initial contents are copied from BOARD0, but whose
     *  undo history is clear, and whose notifier does nothing. */
    Board(Board board0) {
        int n = board0.size();
        _myBoard = new Square[n][n];
        _boardStates = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                Square sq = board0.get(r + 1, c + 1);
                _myBoard[r][c] = square(sq.getSide(), sq.getSpots());
            }
        }
        _readonlyBoard =  new ConstantBoard(this);
        _boardStates.add(new GameState());
        _boardStates.get(0).saveState();
    }

    /** Returns a readonly version of this board. */
    Board readonlyBoard() {
        return _readonlyBoard;
    }

    /** (Re)initialize me to a cleared board with N squares on a side. Clears
     *  the undo history and sets the number of moves to 0. */
    void clear(int N) {
        _myBoard = new Square[N][N];
        _boardStates.clear();
        _numMoves = 0;
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < N; col++) {
                _myBoard[row][col] = square(WHITE, 1);
            }
        }
        _boardStates.add(new GameState());
        _boardStates.get(0).saveState();
    }

    /** Copy the contents of BOARD into me. */
    void copy(Board board) {
        _myBoard = new Square[board.size()][board.size()];
        internalCopy(board);
        _boardStates.clear();
        _numMoves = board._numMoves;
        for (GameState g : board._boardStates) {
            _boardStates.add(g);
        }
    }

    /** Copy the contents of BOARD into me, without modifying my undo
     *  history. Assumes BOARD and I have the same size. */
    private void internalCopy(Board board) {
        assert size() == board.size();
        _myBoard = new Square[board.size()][board.size()];
        for (int row = 0; row < board.size(); row++) {
            for (int col = 0; col < board.size(); col++) {
                Square sq = board.get(row + 1, col + 1);
                _myBoard[row][col] = square(sq.getSide(), sq.getSpots());
            }
        }
    }

    /** Return the number of rows and of columns of THIS. */
    int size() {
        return _myBoard.length;
    }

    /** Returns the contents of the square at row R, column C
     *  1 <= R, C <= size (). */
    Square get(int r, int c) {
        return get(sqNum(r, c));
    }

    /** Returns the contents of square #N, numbering squares by rows, with
     *  squares in row 1 number 0 - size()-1, in row 2 numbered
     *  size() - 2*size() - 1, etc. */
    Square get(int n) {
        return _myBoard[row(n) - 1][col(n) - 1];
    }


    /** Returns the total number of spots on the board. */
    int numPieces() {
        int spots = 0;
        for (Square[] row : _myBoard) {
            for (Square sq: row) {
                spots += sq.getSpots();
            }
        }
        return spots;
    }

    /** Returns the last saved state of board. */
    int lastHistory() {
        return _numMoves - 1;
    }


    /** Returns the Side of the player who would be next to move.  If the
     *  game is won, this will return the loser (assuming legal position). */
    Side whoseMove() {
        return ((numPieces() + size()) & 1) == 0 ? RED : BLUE;
    }

    /** Return true iff row R and column C denotes a valid square. */
    final boolean exists(int r, int c) {
        return 1 <= r && r <= size() && 1 <= c && c <= size();
    }

    /** Return true iff S is a valid square number. */
    final boolean exists(int s) {
        int N = size();
        return 0 <= s && s < N * N;
    }

    /** Return the row number for square #N. */
    final int row(int n) {
        return n / size() + 1;
    }

    /** Return the column number for square #N. */
    final int col(int n) {
        return n % size() + 1;
    }

    /** Return the square number of row R, column C. */
    final int sqNum(int r, int c) {
        return (c - 1) + (r - 1) * size();
    }

    /** Return a string denoting move (ROW, COL)N. */
    String moveString(int row, int col) {
        return String.format("%d %d", row, col);
    }

    /** Return a string denoting move N. */
    String moveString(int n) {
        return String.format("%d %d", row(n), col(n));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
        to square at row R, column C. */
    boolean isLegal(Side player, int r, int c) {
        return isLegal(player, sqNum(r, c));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
     *  to square #N. */
    boolean isLegal(Side player, int n) {
        Square N = get(n);
        return player.equals(N.getSide()) || N.getSide().equals(WHITE);
    }

    /** Returns true iff PLAYER is allowed to move at this point. */
    boolean isLegal(Side player) {
        return player.equals(whoseMove()) && (getWinner() == null);
    }

    /** Returns the winner of the current position, if the game is over,
     *  and otherwise null. */
    final Side getWinner() {
        int reds = numOfSide(RED);
        int blues = numOfSide(BLUE);
        int total = size() * size();
        if (reds == total) {
            return RED;
        } else if (blues == total) {
            return BLUE;
        } else {
            return null;
        }
    }

    /** Return the number of squares of given SIDE. */
    int numOfSide(Side side) {
        int count = 0;
        for (int r = 0; r < size(); r++) {
            for (int c = 0; c < size(); c++) {
                Square curr = _myBoard[r][c];
                if (curr.getSide().equals(side)) {
                    count += 1;
                }
            }
        }
        return count;
    }

    /** Add a spot from PLAYER at row R, column C.  Assumes
     *  isLegal(PLAYER, R, C). */
    void addSpot(Side player, int r, int c) {
        addSpot(player, sqNum(r, c));
    }

    /** Add a spot from PLAYER at square #N.  Assumes isLegal(PLAYER, N). */
    void addSpot(Side player, int n) {
        _numMoves++;
        int currSpot = get(n).getSpots();
        internalSet(n, currSpot + 1, player);
        if ((currSpot + 1) > neighbors(n)) {
            jump(n);
        }
        _boardStates.add(new GameState());
        _boardStates.get(_numMoves).saveState();
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white). */
    void set(int r, int c, int num, Side player) {
        internalSet(r, c, num, player);
        announce();
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white).  Does not announce
     *  changes. */
    private void internalSet(int r, int c, int num, Side player) {
        internalSet(sqNum(r, c), num, player);
    }

    /** Set the square #N to NUM spots (0 <= NUM), and give it color PLAYER
     *  if NUM > 0 (otherwise, white). Does not announce changes. */
    private void internalSet(int n, int num, Side player) {
        _myBoard[row(n) - 1][col(n) - 1] = Square.square(player, num);
    }


    /** Undo the effects of one move (that is, one addSpot command).  One
     *  can only undo back to the last point at which the undo history
     *  was cleared, or the construction of this Board. */
    void undo() {
        if (!_boardStates.isEmpty()) {
            GameState last = _boardStates.get(lastHistory());
            last.restoreState();
            _boardStates.remove(_numMoves);
            _numMoves -= 1;
        }
    }

    /** Record the beginning of a move in the undo history. */
    private void markUndo() {
    }

    /** Add DELTASPOTS spots of side PLAYER to row R, column C,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int r, int c, int deltaSpots) {
        internalSet(r, c, deltaSpots + get(r, c).getSpots(), player);
    }

    /** Add DELTASPOTS spots of color PLAYER to square #N,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int n, int deltaSpots) {
        internalSet(n, deltaSpots + get(n).getSpots(), player);
    }

    /** Used in jump to keep track of squares needing processing.  Allocated
     *  here to cut down on allocations. */
    private final ArrayDeque<Integer> _workQueue = new ArrayDeque<>();

    /** Do all jumping on this board, assuming that initially, S is the only
     *  square that might be over-full. */
    private void jump(int S) {
        if (getWinner() == null) {
            int r = row(S), c = col(S);
            Square curr = get(S);
            Side player = curr.getSide();
            ArrayList<Integer> around = findNeighbors(row(S), col(S));
            for (int neigh: around) {
                internalSet(neigh, get(neigh).getSpots() + 1, player);
                internalSet(S, get(S).getSpots() - 1, player);
                if (get(neigh).getSpots() > neighbors(neigh)) {
                    _workQueue.add(neigh);
                }
            }
            while (getWinner() == null && !_workQueue.isEmpty()) {
                Integer n = _workQueue.remove();
                ArrayList<Integer> neighbors = findNeighbors(row(n), col(n));
                internalSet(n, 1, player);
                for (int N : neighbors) {
                    if (get(N).getSpots() > neighbors(N)) {
                        _workQueue.add(N);
                    } else {
                        internalSet(N, get(N).getSpots() + 1, player);
                        if (get(N).getSpots() > neighbors(N)) {
                            _workQueue.add(N);
                        }
                    }
                }
            }
            _workQueue.clear();
        }
    }

    /** Returns my dumped representation. */
    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("%n===%n");
        for (int r = 1; r <= size(); r++) {
            out.format("    ");
            for (int c = 1; c <= size(); c++) {
                Square sq = get(r, c);
                Side s = sq.getSide();
                String ss = s.toString();
                if (s.equals(WHITE)) {
                    ss = "-";
                }
                out.format("%d%s ", sq.getSpots(), ss.substring(0, 1));
            }
            out.format("%n");
        }
        out.format("===");
        return out.toString();
    }

    /** Returns an external rendition of me, suitable for human-readable
     *  textual display, with row and column numbers.  This is distinct
     *  from the dumped representation (returned by toString). */
    public String toDisplayString() {
        String[] lines = toString().trim().split("\\R");
        Formatter out = new Formatter();
        for (int i = 1; i + 1 < lines.length; i += 1) {
            out.format("%2d %s%n", i, lines[i].trim());
        }
        out.format("  ");
        for (int i = 1; i <= size(); i += 1) {
            out.format("%3d", i);
        }
        return out.toString();
    }

    /** Returns the number of neighbors of the square at row R, column C. */
    int neighbors(int r, int c) {
        int size = size();
        int n;
        n = 0;
        if (r > 1) {
            n += 1;
        }
        if (c > 1) {
            n += 1;
        }
        if (r < size) {
            n += 1;
        }
        if (c < size) {
            n += 1;
        }
        return n;
    }

    /** Returns the number of neighbors of square #N. */
    int neighbors(int n) {
        return neighbors(row(n), col(n));
    }

    /** Returns an ArrayList containing the neighbors of square #N using
     * row R and col C. */
    ArrayList<Integer> findNeighbors(int r, int c) {
        ArrayList<Integer> neighbors = new ArrayList<>();
        if (exists(r - 1, c)) {
            neighbors.add(sqNum(r - 1, c));
        }
        if (exists(r + 1, c)) {
            neighbors.add(sqNum(r + 1, c));
        }
        if (exists(r, c - 1)) {
            neighbors.add(sqNum(r, c - 1));
        }
        if (exists(r, c + 1)) {
            neighbors.add(sqNum(r, c + 1));
        }
        return neighbors;
    }

    /** Returns _boardStates for testing purposes. */
    ArrayList<GameState> getBoardStates() {
        return _boardStates;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Board)) {
            return false;
        } else {
            Board B = (Board) obj;
            return this == obj;
        }
    }

    @Override
    public int hashCode() {
        return numPieces();
    }

    /** Set my notifier to NOTIFY. */
    public void setNotifier(Consumer<Board> notify) {
        _notifier = notify;
        announce();
    }

    /** Take any action that has been set for a change in my state. */
    private void announce() {
        _notifier.accept(this);
    }


    /**Represents enough of the state of a game to allow undoing
     * and redoing of moves. */
    private class GameState {

        /**A holder for the _myBoard instance variable of Board.*/
        GameState() {
            _state = new Square[size()][size()];
        }

        /** Initialize to the current state of the Model. */
        void saveState() {
            deepCopy(_myBoard, _state);
        }

        /** Copies SRC onto DEST. */
        void deepCopy(Square[][] src, Square[][] dest) {
            assert src.length == dest.length && src[0].length == dest[0].length;
            for (int i = 0; i < src.length; i++) {
                for (int j = 0; j < src.length; j++) {
                    dest[i][j] = src[i][j];
                }
            }
        }

        /** Restore the current Model's state from our saved state. */
        void restoreState() {
            deepCopy(_state, _myBoard);
        }

        /** Contents of board. */
        private Square[][] _state;

    }

    /** A notifier that does nothing. */
    private static final Consumer<Board> NOP = (s) -> { };

    /** A read-only version of this Board. */
    private ConstantBoard _readonlyBoard;

    /** Use _notifier.accept(B) to announce changes to this board. */
    private Consumer<Board> _notifier;

    /** Structure that stores the Square objects that represents each square. */
    private Square[][] _myBoard;

    /** A sequence of board states. The initial board is at index 0.
     *  _boardStates[_numMoves] is equal to the current board state.
     *  _boardStates[0] through _boardStates[_lastHistory] are undone
     *  states that can be redone.  _lastHistory is reset to _numMoves - 1 after
     *  each move. */
    private ArrayList<GameState> _boardStates;

    /** The total number of moves that have been made. This indicates current
     * position in _boardStates. The value is non-negative. */
    private int _numMoves;

}
