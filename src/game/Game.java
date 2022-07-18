package game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import boardcomponents.Board;

public class Game {
    
    private Board board; // The board for the game
    private boolean whiteToPlay; // Determines whether it is white's turn to play
    private boolean whiteAI; // Determines the color of the AI
    private int fullmoveCount; // The number of completed turns in the game
    private TranspositionTable transTable; // Hash table used to save time analyzing identical board states

    // The first four moves from grandmaster games, found on chess.com
    private int selectedAIOpening; // The AI's selected opening
	private static final String[][] whiteOpenings = {{"d2d4", "c2c4", "b1c3", "g1f3"},
                                                    {"e2e4", "d2d4", "g1f3", "c2c4"},
                                                    {"e2e4", "b1c3", "f2f4", "h2h4"},
                                                    {"d2d4", "c2c4", "b1c3", "f2f3"},
                                                    {"e2e4", "g1f3", "d2d4", "b2b4"},
                                                    {"e2e4", "g1f3", "f1c4", "c2c3"},
                                                    {"b2b4", "c1b2", "e2e4", "f2f4"},
                                                    {"g1f3", "b1c3", "b2b3", "c1b2"},
                                                    {"g1f3", "b2b3", "c1b2", "g2g3"},
                                                    {"g1f3", "e2e4", "d2d4", "f1d3"},
                                                    {"e2e4", "b1c3", "d2d4", "g1f3"},
                                                    {"d2d4", "g1f3", "c1f4", "g2g3"},
                                                    {"d2d4", "c2c4", "g2g3", "g1f3"},
                                                    {"g1f3", "e2e4", "d2d4", "b2b3"}};
    private static final String[][] blackOpenings = {{"e7e6", "d7d5", "c7c5", "b8c6"},
                                                    {"g8f6", "e7e6", "d7d5", "f8e7"},
                                                    {"e7e5", "b8c6", "g8f6", "b7b5"},
                                                    {"d7d5", "c8g4", "c7c6", "d8c7"},
                                                    {"g7g6", "f8g7", "a7a6", "c7c6"},
                                                    {"e7e5", "b8c6", "g8f6", "d7d6"},
                                                    {"c7c5", "e7e5", "d7d6", "b8c6"},
                                                    {"e7e5", "b8c6", "g7g6", "f7f5"},
                                                    {"d7d5", "c7c6", "g8h6", "e7e6"},
                                                    {"g8f6", "e7e6", "c7c5", "d7d6"},
                                                    {"g8f6", "e7e6", "f8b4", "c7c5"},
                                                    {"c7c5", "d7d6", "g8f6", "b7b6"},
                                                    {"g8f6", "f6d5", "d7d6", "g7g6"},
                                                    {"c7c5", "e7e6", "f7f5", "g8h6"}};

	private String bestMove; // The move currently considered the best by the search algorithm
	private String bestMoveAfterSearch; // The move considered the best after a complete search at a specified depth
	private int currentDepth; // The depth that the search algorithm is currently searching
	private final static long timeLimit = 2000; // We limit the search to 2s
	private long searchStartTime; // The time the current search began
	private boolean timeout; // Determines whether the search should be halted


    // Creates a game using the default setup and allows the user to select their color
	public Game(boolean playAsWhite) {
		board = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0");
        whiteToPlay = true;
		fullmoveCount = 0;
        whiteAI = !playAsWhite;
		transTable = new TranspositionTable();
	}

    // Creates a game based on a starting FEN string.
	public Game(String fen, boolean playAsWhite) {
		String[] fenFields = fen.split(" ");

		whiteToPlay = fenFields[1].compareTo("w") == 0;
		board = new Board(fen);
        whiteAI = !playAsWhite;
		fullmoveCount = Integer.parseInt(fenFields[5]);
		transTable = new TranspositionTable();
	}

    // Resets the game, allowing the user to select a new fen string
	public void reset(String fen, boolean playAsWhite) {
		String[] fenFields = fen.split(" ");

		whiteToPlay = fenFields[1].compareTo("w") == 0;
		board = new Board(fen);
		fullmoveCount = Integer.parseInt(fenFields[5]);
	}

    // Return the board
    public Board getBoard() {
        return board;
    }

    // Set fullmove count
    public void setFullmoveCount() {
        fullmoveCount = 5;
    }

    // Makes a move for the player whose turn it currently is
    // If the move provided is illegal, then false is returned
    public boolean makeMove(String move) {
        if (move.length() < 4 || move.length() > 5) {
            return false;
        }

        HashSet<String> legalMoves = MoveGeneration.legalMoves(board);
        if (legalMoves.contains(move) && !MoveGeneration.capturesKing(move)) {
            char promotion = 'x';
            if (move.length() == 5) {
                promotion = move.charAt(4);
            }

            int origin = Board.toNumericNotation(move.substring(0, 2));
            int target = Board.toNumericNotation(move.substring(2, 4));
            board.makeMove(origin, target, promotion);

            if (!whiteToPlay) {
                fullmoveCount++;
            }

            whiteToPlay = !whiteToPlay;

            return true;
        }

        return false;
    }

    // Has the AI make a move
	// Returns the move
	public String aiMove() {
		bestMoveAfterSearch = null;
		if (whiteToPlay && !whiteAI) {
            return "";
        }

		// Select an opening
		if (fullmoveCount == 0) {
			Random rand = new Random();
            int numberOfOpenings = whiteAI ? whiteOpenings.length : blackOpenings.length;
            selectedAIOpening = rand.nextInt(numberOfOpenings);
		}

		// Make a move based on the opening
		String move;
		if (fullmoveCount < 3 && selectedAIOpening != -1) {
            String[] opening = whiteAI ? whiteOpenings[selectedAIOpening] : blackOpenings[selectedAIOpening];
			move = opening[fullmoveCount];
			if (!makeMove(move)) {
				selectedAIOpening = -1;
				move = bestMove();
				makeMove(move);
			}
		} else { // Simply choose the best move via minimax
			move = bestMove();
			makeMove(move);
		}

		return move;
	}

	// Returns whether the current player has been checkmated
	public boolean checkmate(boolean whitePlayer) {
		return MoveGeneration.legalMoves(board).size() == 0;
	}

    // Given a depth of n ply, this will return the number of possible board states to verify the algorithm
	// is properly generating legal moves
	public int moveGenerationTest(int depth) {
		if (depth == 0) {
			return 1;
		}
		
        // Iterate over each move
        int numPositions = 0;
		ArrayList<String> moves = MoveGeneration.generateMoves(board);
        Iterator<String> moveItr = moves.iterator();
        while (moveItr.hasNext()) {
            String move = moveItr.next();
            if (makeMove(move)) {
                numPositions += moveGenerationTest(depth - 1);
                undoMove();
            }
        }
		
		return numPositions;
	}

    // Move generation test with diagnostics, showing the number of board positions after each of the first moves
	public int moveGenerationDiagnostics(int depth) {
		// depth must be 1 or greater
		if (depth >= 1) {
			ArrayList<String> moves = MoveGeneration.generateMoves(board);
            Iterator<String> moveItr = moves.iterator();
			int total = 0;

			// Iterate over each move
            while (moveItr.hasNext()) {
                String move = moveItr.next();
                if (makeMove(move)) {
                    // Test each of the first moves
                    int numAdded = moveGenerationTest(depth - 1);
                    total += numAdded;

                    System.out.println(move + ": " + numAdded);
                    undoMove();
                } else {
                    System.out.println(move + ": Illegal");
                }
            }

			return total;
		}
		
		return -1;
	}

    // A test suite, where the behavior of this chess engine is compared to Stockfish's perft results
	// The Stockfish engine can be found here https://github.com/official-stockfish/Stockfish
	public void testSuite() {
		String[] fen = {"1k6/1b6/8/8/7R/8/8/4K2R b K - 0 1", "3k4/3p4/8/K1P4r/8/8/8/8 b - - 0 1", "8/8/4k3/8/2p5/8/B2P2K1/8 w - - 0 1", "1rk2b1r/pp3p1p/q2pbPp1/1Np5/n1PpP3/1Q3P1N/1P1B3P/R3KB1R w K - 1 17",
						"8/8/1k6/2b5/2pP4/8/5K2/8 b - d3 0 1", "5k2/8/8/8/8/8/8/4K2R w K - 0 1", "3k4/8/8/8/8/8/8/R3K3 w Q - 0 1", "r3k2r/1b4bq/8/8/8/8/7B/R3K2R w KQkq - 0 1", "r3k2r/8/3Q4/8/8/5q2/8/R3K2R b KQkq - 0 1",
						"2K2r2/4P3/8/8/8/8/8/3k4 w - - 0 1", "8/8/1P2K3/8/2n5/1q6/8/5k2 b - - 0 1", "4k3/1P6/8/8/8/8/K7/8 w - - 0 1", "8/P1k5/K7/8/8/8/8/8 w - - 0 1", "K1k5/8/P7/8/8/8/8/8 w - - 0 1",
						"8/k1P5/8/1K6/8/8/8/8 w - - 0 1", "8/8/2k5/5q2/5n2/8/5K2/8 b - - 0 1", "7N/pb5p/1N1bkPp1/2p5/2PpP3/5P2/1P5P/4KB1R w K - 3 28", "r3k1nr/p1p1ppb1/1pn5/6Bp/3P1Pb1/2P4N/PP3K1P/RN1Q1B1q w kq - 0 10",
						"r3k1r1/8/p1p5/3pP2p/1q1P3P/2b1BPpN/2Q3P1/5K1R b q - 0 29", "2k3r1/8/p1p4r/3pP1Np/1Q1P3P/5Pp1/3K2P1/5q2 b - - 5 38", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
						"rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 0",
						"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"};
		int[] ply = {5, 6, 6, 4, 6, 6, 6, 4, 4, 6, 5, 6, 6, 6, 7, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5};
		int[] expectedNumPositions = {1063513, 1134888, 1015133, 1166463, 1440467, 661072, 803711, 1274206, 1720476, 3821001, 1004658, 217342, 92683, 2217, 567584, 23527, 230090, 1817527, 1278243, 400719, 3894594, 2103487,
									  422333, 674624, 4865609};
		int numTests = fen.length;

		for (int test = 0; test < numTests; test++) {
			reset(fen[test], true);
			long startTime = System.nanoTime();
			int observedNumPositions = moveGenerationTest(ply[test]);
			long endTime = System.nanoTime();
			String testOutcome = observedNumPositions == expectedNumPositions[test] ? "Passed" : "Failed";
			System.out.println("Test " + test + ": Observed Number of Positions: " + observedNumPositions + " Time: " + (endTime - startTime) / 1000000 + " ms Test Outcome: " + testOutcome + " Discrepancy: " + Math.abs(expectedNumPositions[test] - observedNumPositions));
		}
	}

    // Undoes a move, returning the board to the state before the last move (if a previous move was made)
	public void undoMove() {
		board.undoMove();
	}

    // Returns a score evaluating this state of the board
	// A positive score indicates a good score for the player currently playing
	// A negative score indicates a bad score for the player currently playing
	public double evaluateBoard() {
		return board.currentEval() * (whiteToPlay ? 1 : -1);
	}

	// Searches for the best possible move using the minimax algorithm with alpha-beta pruning
	public double negamaxSearch(int depth, double alpha, double beta) {
		// Do not continue searching if the time is up
		if (System.currentTimeMillis() - searchStartTime > timeLimit && bestMoveAfterSearch != null) {
			timeout = true;
			return alpha;
		}

		// For leaf nodes, evaluate the board position
		if (depth == 0) {
			if (transTable.contains(board)) {
				return transTable.get(board);
			} else {
				double eval = board.currentEval();
				transTable.put(board, eval);
				return eval;
			}
		}

		ArrayList<String> moves = MoveGeneration.generateMoves(board);
		// There are no moves that can be made
		if (moves.isEmpty()) {
			// The player has been checkmated, so return evaluation of negative infinity
			if (MoveGeneration.inCheck(whiteToPlay)) {
				return Double.NEGATIVE_INFINITY;
			}
			// The player has been stalemated, so return evaluation of 0
			return 0;
		}

		// Look for best option for this player
		for (String move : moves) {
			if (makeMove(move)) {
				double evaluation;
				evaluation = -negamaxSearch(depth - 1, -beta, -alpha);

				undoMove();
				if (evaluation >= beta) {
					return beta; // The opponent will avoid this position because the move was too good
				}

				if (evaluation > alpha) {
					alpha = evaluation;

					if (depth == currentDepth) {
						bestMove = move;
					}
				}
			}
		}

		return alpha;
	}

	// Finds the best move for the current player
	public String bestMove() {
		timeout = false;
		searchStartTime = System.currentTimeMillis();

		// Iterative deepening
		for (currentDepth = 4; ; currentDepth++) {
			if (currentDepth > 4) {
				bestMoveAfterSearch = bestMove; // Only update the bestMoveAfterSearch if a previous search completed
				System.out.println("Completed search at a depth of " + (currentDepth - 1) + " best move so far " + bestMoveAfterSearch);
			}

			negamaxSearch(currentDepth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

			if (timeout && bestMoveAfterSearch != null) {
				System.out.println("Took " + (System.currentTimeMillis() - searchStartTime) + " milliseconds");
				return bestMoveAfterSearch;
			}
		}
	}

    // Prints the board from white's perspective
	public void printBoardWhitePerspective() {
		long[] bitboards = board.pieceBitboards();
        String[][] boardString = new String[9][9];
        String[] pieces = {" K ", " Q ", " R ", " B ", " N ", " P ", " k ", " q ", " r ", " b ", " n ", " p "};

        // Add empty tiles to string
        for (int i = 0; i < 64; i++) {
            boardString[i / 8][i % 8] = " - ";
        }

        // Add pieces to string
        ArrayList<Integer> bitPositions;
        for (int i = 0; i < 12; i++) {
            bitPositions = MoveGeneration.bitboardToBitPositions(bitboards[i]);
            for (Integer bitPosition : bitPositions) {
                boardString[(63 - bitPosition) / 8][(63 - bitPosition) % 8] = pieces[i];
            }
        }

		// Add algebraic notation guide
		for (int i = 0; i < 8; i++) {
			boardString[i][8] = Integer.toString(8 - i);
		}
		for (int i = 0; i < 8; i++) {
			boardString[8][i] = " " + Character.toString((char)(i + 97)) + " ";
		}
		boardString[8][8] = "-";

        // Print each row
        for (int i = 0; i < 9; i++) {
		    System.out.println(Arrays.toString(boardString[i]));
        }

        System.out.println();
	}

	// Prints the board from black's perspective
	public void printBoardBlackPerspective() {
		long[] bitboards = board.pieceBitboards();
        String[][] boardString = new String[9][9];
        String[] pieces = {" K ", " Q ", " R ", " B ", " N ", " P ", " k ", " q ", " r ", " b ", " n ", " p "};

        // Add empty tiles to string
        for (int i = 0; i < 64; i++) {
            boardString[i / 8][i % 8] = " - ";
        }

        // Add pieces to string
        ArrayList<Integer> bitPositions;
        for (int i = 0; i < 12; i++) {
            bitPositions = MoveGeneration.bitboardToBitPositions(bitboards[i]);
            for (Integer bitPosition : bitPositions) {
                boardString[bitPosition / 8][bitPosition % 8] = pieces[i];
            }
        }

		// Add algebraic notation guide
		for (int i = 0; i < 8; i++) {
			boardString[i][8] = Integer.toString(i + 1);
		}
		for (int i = 0; i < 8; i++) {
			boardString[8][i] = " " + Character.toString((char)(104 - i)) + " ";
		}
		boardString[8][8] = "-";

        // Print each row
        for (int i = 0; i < 9; i++) {
		    System.out.println(Arrays.toString(boardString[i]));
        }

        System.out.println();
	}

	// Prints the board from the player's perspective
	public void printBoard(boolean playerIsWhite) {
		if (playerIsWhite) {
			printBoardWhitePerspective();
		} else {
			printBoardBlackPerspective();
		}
	}
}
