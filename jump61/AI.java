package jump61;

import java.util.ArrayList;
import java.util.Random;

import static jump61.Side.*;

/** An automated Player.
 *  @author P. N. Hilfinger
 */
class AI extends Player {

    /** A new player of GAME initially COLOR that chooses moves automatically.
     *  SEED provides a random-number seed used for choosing moves.
     */
    AI(Game game, Side color, long seed) {
        super(game, color);
        _random = new Random(seed);
    }

    @Override
    String getMove() {
        Board board = getGame().getBoard();
        assert getSide() == board.whoseMove();
        int choice = searchForMove();
        getGame().reportMove(board.row(choice), board.col(choice));
        return String.format("%d %d", board.row(choice), board.col(choice));
    }

    /** Return a move after searching the game tree to DEPTH>0 moves
     *  from the current position. Assumes the game is not over. */
    private int searchForMove() {
        Board work = new Board(getBoard());
        assert getSide() == work.whoseMove();
        _foundMove = -1;
        if (getSide() == RED) {
            minMax(work, 4, true, 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        } else {
            minMax(work, 4, true, -1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        return _foundMove;
    }


    /** Find a move from position BOARD and return its value, recording
     *  the move found in _foundMove iff SAVEMOVE. The move
     *  should have maximal value or have value > BETA if SENSE==1,
     *  and minimal value or value < ALPHA if SENSE==-1. Searches up to
     *  DEPTH levels.  Searching at level 0 simply returns a static estimate
     *  of the board value and does not set _foundMove. If the game is over
     *  on BOARD, does not set _foundMove. */
    private int minMax(Board board, int depth, boolean saveMove,
        int sense, int alpha, int beta) {
        int bestValue, response, bestMove = 0;
        if (board.getWinner() != null || depth == 0) {
            return staticEval(board, 1000);
        } else if (sense == 1) {
            bestValue = Integer.MIN_VALUE;
            for (int N = 0; N < board.size() * board.size(); N++) {
                if (board.isLegal(RED, N)) {
                    Board next = new Board(board);
                    next.addSpot(RED, N);
                    response = minMax(next, depth - 1, false, -1, alpha, beta);
                    next.undo();
                    if (response > bestValue) {
                        bestValue = response;
                        alpha = Math.max(alpha, response);
                        bestMove = N;
                    }
                    if (alpha >= beta) {
                        break;
                    }
                }
            }
        } else {
            bestValue = Integer.MAX_VALUE;
            for (int N = 0; N < board.size() * board.size(); N++) {
                if (board.isLegal(BLUE, N)) {
                    Board next = new Board(board);
                    next.addSpot(BLUE, N);
                    response = minMax(next, depth - 1, false, 1, alpha, beta);
                    next.undo();
                    if (response < bestValue) {
                        bestValue = response;
                        beta = Math.min(beta, response);
                        bestMove = N;
                    }
                    if (alpha >= beta) {
                        break;
                    }
                }
            }
        }
        if (saveMove) {
            _foundMove = bestMove;
        }
        return bestValue;
    }


    /** Return a heuristic estimate of the value of board position B.
     *  Use WINNINGVALUE to indicate a win for Red and -WINNINGVALUE to
     *  indicate a win for Blue. */
    private int staticEval(Board b, int winningValue) {
        if (b.getWinner() == RED) {
            return winningValue;
        } else if (b.getWinner() == BLUE) {
            return -winningValue;
        } else {
            int reds = b.numOfSide(RED);
            int blues = b.numOfSide(BLUE);
            return reds - blues;
        }
    }


    /** A random-number generator used for move selection. */
    private Random _random;

    /** Used to convey moves discovered by minMax. */
    private int _foundMove;

    /** Possible moves of a player at a turn. */
    private ArrayList<Integer> moves = new ArrayList<>();
}
