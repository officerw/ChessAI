package boardcomponents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Stack;

import game.MoveGeneration;

public class Board {

    // A bitboard is used because it allows for efficient move generation on 64-bit machines
    // For move generation, the computer only needs to do bitwise arithmetic
    // For a bitboard, we represent the board with 12 64-bit long's, one for each piece/color combination
    // When a piece is at a certain position on the bitboard, the bit with the same position will be set to 1

    // Zobrist Hashing Info.
    private static Random rand = new Random(); // Random object used to generate zobrist keys
    private long[][] zobristKeys = new long[12][64]; // Zobrist keys, used to store board states with zobrist hashes
	// The order for the zobrist keys is K, Q, R, B, N, P, k, q, r, b, n, p, where uppercase represents the key for a white piece and vice versa

    // Piece information
	private int pieceCount; // The number of pieces on the board
    private long[] bitboards = new long[12]; // There are 12 piece bitboards, one for each piece/color combination
    // The piece bitboards is K, Q, R, B, N, P, k, q, r, b, n, p, where uppercase represents the key for a white piece and vice versa

    // En passant and castling rights information
    private long firstMoves; // Stores information on what pieces have not moved before
    private long enPassant; // Stores the location of where pawns can en passant capture for the current board state

    // Determines whether it is white's turn to move
    private boolean whiteToMove;

    // Board history, used for undoing moves
    private Stack<BoardState> boardHistory;
    private double currentEval;
	
    // Initializes the board state using Forsyth-Edwards Notation
	// Credit to Chess.com for information on FEN.
	// Assumes a valid FEN string.
	public Board(String fen) {
        String[] fields = fen.split(" ");
		pieceCount = 0;
        boardHistory = new Stack<BoardState>();

		// Generate zobrist keys
		for (int position = 0; position < 64; position++) {
			for (int key = 0; key < 12; key++) {
				zobristKeys[key][position] = rand.nextLong();
			}
		}

        // Determines whether it is white's or black's turn to move
        if (fields[1].compareTo("w") == 0) {
            whiteToMove = true;
        } else {
            whiteToMove = false;
        }

		// Sets castling rights
		boolean whiteKingsideCastle = fields[2].contains("K");
		boolean whiteQueensideCastle = fields[2].contains("Q");
		boolean blackKingsideCastle = fields[2].contains("k");
		boolean blackQueensideCastle = fields[2].contains("q");

		// Sets en passant rights
		if (fields[3].compareTo("-") != 0) {
            enPassant = 1L << toNumericNotation(fields[3]);
		}

		// Sets piece placement
		int fieldOneLength = fields[0].length();
	    long currentTile = 1L << 63;
		for (int i = 0; i < fieldOneLength; i++) {
			Character ch = fields[0].charAt(i);
			int colorIdxOffset = Character.isUpperCase(ch) ? 0 : 6;
			
			if (ch.compareTo('/') == 0) {
				// Go to next row
                continue;
			} else if (Character.isDigit(ch)) {
                // Skip the specified number of tiles
				currentTile = currentTile >>> (ch - '0');
			} else {
                // Add piece to the correct bitboard
				switch (Character.toLowerCase(ch)) {
					case 'p':
                        bitboards[5 + colorIdxOffset] += currentTile;
                        
                        // Set pawn to be in its first move if in the correct row for the given color
                        if (colorIdxOffset == 0) {
                            firstMoves += currentTile & 0x000000000000FF00L;
                        } else {
                            firstMoves += currentTile & 0x00FF000000000000L;
                        }

						pieceCount++;
						break;
					case 'n':
                        bitboards[4 + colorIdxOffset] += currentTile;

                        // Set knight to be in its first move if in the correct position for the given color
                        if (colorIdxOffset == 0) {
                            firstMoves += currentTile & 0x0000000000000042L;
                        } else {
                            firstMoves += currentTile & 0x4200000000000000L;
                        }
                        
						pieceCount++;
						break;
					case 'b':
                        bitboards[3 + colorIdxOffset] += currentTile;
                        
                        // Set bishop to be in its first move if in the correct position for the given color
                        if (colorIdxOffset == 0) {
                            firstMoves += currentTile & 0x0000000000000024L;
                        } else {
                            firstMoves += currentTile & 0x2400000000000000L;
                        }

						pieceCount++;
						break;
                    case 'r':
                        bitboards[2 + colorIdxOffset] += currentTile;
                        
                        // Set rook to be in its first move if in the correct position for the given color
                        if (colorIdxOffset == 0) {
                            if ((currentTile & 1L) != 0 && whiteKingsideCastle) {
                                firstMoves += currentTile;
                            } else if ((currentTile & 0x80L) != 0 && whiteQueensideCastle) {
                                firstMoves += currentTile;
                            }
                        } else {
                            if ((currentTile & 0x100000000000000L) != 0 && blackKingsideCastle) {
                                firstMoves += currentTile;
                            } else if ((currentTile & 0x8000000000000000L) != 0 && blackQueensideCastle) {
                                firstMoves += currentTile;
                            }
                        }

						pieceCount++;
						break;
					case 'q':
                        bitboards[1 + colorIdxOffset] += currentTile;
                        
                        // Set queen to be in its first move if in the correct position for the given color
                        if (colorIdxOffset == 0) {
                            firstMoves += currentTile & 0x0000000000000010L;
                        } else {
                            firstMoves += currentTile & 0x1000000000000000L;
                        }

						pieceCount++;
						break;
					case 'k':
                        bitboards[colorIdxOffset] += currentTile;
                        
                        // Set king to be in its first move if in the correct position for the given color
                        if (colorIdxOffset == 0) {
                            if (whiteKingsideCastle || whiteQueensideCastle) {
                                firstMoves += currentTile & 0x0000000000000008L;
                            }
                        } else {
                            if (blackKingsideCastle || blackQueensideCastle) {
                                firstMoves += currentTile & 0x0800000000000000L;
                            }
                        }

						pieceCount++;
						break;
					default: 
						break;
				}
				
				currentTile = currentTile >>> 1;
			}
		}

        int[] materialWeight = {10000, 1000, 500, 350, 300, 100};
        for (int i = 0; i < 12; i++) {
            int colorModifier = i < 6 ? 1 : -1;
            
            currentEval += colorModifier * (Long.bitCount(bitboards[i]) * materialWeight[i % 6]);
        }
	}

    // Makes a move, saving the current state and updating the board state accordingly
    // Assumes the move is legal
    public void makeMove(int origin, int target, char promotion) {
        boardHistory.add(new BoardState(this));
        
        int pieceBitboard = 0; // Determines which bitboard this piece belongs to
        int capturedBitboard = 0;
        long piecePosition = 1L << origin;
        long targetPosition = 1L << target;
        long occupied = 0L; // Create a bitboard for occupied tiles

        for (int i = 0; i < 12; i++) {
            occupied |= bitboards[i];

            if ((bitboards[i] & piecePosition) != 0) {
                pieceBitboard = i;
            }
        }

        long capturedPosition = targetPosition;
        boolean movingToOccupiedSquare = (targetPosition & occupied) != 0; 
        boolean isPawn = ((bitboards[5] | bitboards[11]) & piecePosition) != 0;
        boolean isEnPassant = isPawn && (origin % 8 != target % 8) && !movingToOccupiedSquare;
        // Adjust captured position if capturing via en passant
        if (isEnPassant) {
            capturedPosition = targetPosition << 8 | targetPosition >>> 8;
        }
        
        boolean pawnMovingTwoSpaces = isPawn && Math.abs(target - origin) == 16;
        boolean isCastling = ((bitboards[0] | bitboards[6]) & piecePosition) != 0 && Math.abs(target - origin) == 2;
        boolean promoting = promotion != 'x';

        // Decrement piece if a piece is being captured
        firstMoves &= ~(targetPosition | piecePosition);
        if (isEnPassant || movingToOccupiedSquare) {
            firstMoves &= ~capturedPosition;
            pieceCount--;
        }

        // If the pawn moved two spaces, then set the en passant square
        if (pawnMovingTwoSpaces) {
            enPassant = 1L << (origin + target) / 2;
        } else {
            enPassant = 0L;
        }

        // Remove moved piece from original position
        bitboards[pieceBitboard] &= ~piecePosition;

        // Remove captured piece
        int enemyColorOffset = whiteToMove ? 6 : 0;
        for (int i = 0; i < 6; i++) {
            if (Long.bitCount(bitboards[i + enemyColorOffset] & capturedPosition) != 0) {
                capturedBitboard = i;
                bitboards[i + enemyColorOffset] &= ~capturedPosition;
            }
        }

        // Place piece at new position
        if (!promoting) {
            bitboards[pieceBitboard] |= targetPosition;
        } else {
            int promotionBitboard = 0;
            int colorOffset = whiteToMove ? 0 : 6;
            switch (promotion) {
                case 'q':
                    promotionBitboard = 1;
                    break;
                case 'r':
                    promotionBitboard = 2;
                    break;
                case 'b':
                    promotionBitboard = 3;
                    break;
                case 'n':
                    promotionBitboard = 4;
                    break;
                default:
                    promotionBitboard = 1;
                    break;
            }

            bitboards[promotionBitboard + colorOffset] |= targetPosition;
        }

        // If castling, move the respective rook
        if (isCastling) {
            boolean queensideCastle = target > origin;
            long rookPos;

            if (whiteToMove && queensideCastle) {
                rookPos = 1L << 7;
                bitboards[2] &= ~rookPos; // Remove the rook
                firstMoves &= ~rookPos; // Count the rook as having moved
                bitboards[2] |= rookPos >>> 3; // Add the rook
            } else if (whiteToMove) {
                rookPos = 1L;
                bitboards[2] &= ~rookPos; // Remove the rook
                firstMoves &= ~rookPos; // Count the rook as having moved
                bitboards[2] |= rookPos << 2; // Add the rook
            } else if (!whiteToMove && queensideCastle) {
                rookPos = 1L << 63;
                bitboards[8] &= ~rookPos; // Remove the rook
                firstMoves &= ~rookPos; // Count the rook as having moved
                bitboards[8] |= rookPos >>> 3; // Add the rook
            } else if (!whiteToMove) {
                rookPos = 1L << 56;
                bitboards[8] &= ~rookPos; // Remove the rook
                firstMoves &= ~rookPos; // Count the rook as having moved
                bitboards[8] |= rookPos << 2; // Add the rook
            }
        }

        // Update evaluation
        int[] materialWeight = {10000, 1000, 500, 350, 300, 100};;
        int colorModifier = whiteToMove ? 1 : -1;
        long[] attacks = MoveGeneration.attacks();
        long[] defended = MoveGeneration.defended();
        int enemy = whiteToMove ? 1 : 0;
        int ally = whiteToMove ? 0 : 1;
        if (isEnPassant || movingToOccupiedSquare) {
            double pieceValueDifference = Math.pow(pieceBitboard % 6 - capturedBitboard % 6, 2) * 75;

            currentEval += colorModifier * materialWeight[capturedBitboard % 6];
            // Encourage the capture of valuable pieces with less valuable pieces
            currentEval += colorModifier * pieceValueDifference;

            // Encourage the capture of undefended pieces
            if ((~defended[enemy] & capturedPosition) != 0) {
                currentEval += colorModifier * 500;
            }
        }

        // Encourage the advancement of pawns
        if (pieceBitboard % 6 == 5) {
            currentEval += colorModifier * 50;
        }

        // Encourage increased mobility
        currentEval += colorModifier * 5 * Long.bitCount(attacks[whiteToMove ? 0 : 1]);

        // Discourage valuable pieces from moving to attacked or defended territory, unless it is defended by an ally
        if ((attacks[enemy] & targetPosition) != 0 && (attacks[ally] & targetPosition) == 0) {
            currentEval -= colorModifier * materialWeight[pieceBitboard % 6] * 2;
        }

        whiteToMove = !whiteToMove;
    }

    // Undoes a move, restoring the board to the most recently stored board state
    public void undoMove() {
        if (!boardHistory.isEmpty()) {
            boardHistory.pop().restore(this);
        }
    }

    // Returns the bitboards for the pieces
    public long[] pieceBitboards() {
        return bitboards;
    }

    // Returns the current piece count
    public int pieceCount() {
        return pieceCount;
    }

    // Returns positions attackable via en passant
    public long enPassant() {
        return enPassant;
    }

    // Returns the current piece evaluation
    public double currentEval() {
        return currentEval;
    }

    // Removes the ability to capture a pawn via en passant
    public void removeEnPassant() {
        enPassant = 0L;
    }

    // Returns positions of pieces that have not moved
    public long firstMoves() {
        return firstMoves;
    }

    // Returns whether it is white's turn to move
    public boolean whiteToMove() {
        return whiteToMove;
    }

    // Restores this board to a previous state using the provided values
    public void restore(int prevCount, long[] prevBitboards, long prevFirstMoves, long prevEnPassant, boolean prevWhiteToMove, double eval) {
        pieceCount = prevCount;
        bitboards = prevBitboards;
        firstMoves = prevFirstMoves;
        enPassant = prevEnPassant;
        whiteToMove = prevWhiteToMove;
        currentEval = eval;
    }

    // Get the board state before the last move
    public BoardState getLastBoardState() {
        return boardHistory.peek();
    }

    // Calculates the zobrist hash for this board state
    public long zobristHash() {
        long hashCode = 0L;

        // Iterate over each bitboard
        for (int key = 0; key < 12; key++) {
            long currBitboard = bitboards[key];
            ArrayList<Integer> bitPositions = MoveGeneration.bitboardToBitPositions(currBitboard);

            for (Integer bitPosition : bitPositions) {
                hashCode ^= zobristKeys[key][bitPosition];
            }
        }

        return hashCode;
    }

    // Prints a given bitboard
    public static void printBitboard(long bitboard) {
        String bitboardStr = Long.toBinaryString(bitboard);
        StringBuilder sb = new StringBuilder(bitboardStr);

        // Adds 0's to add leading zeroes
        int numZeroesToAdd = 64 - bitboardStr.length();
        for (int i = 0; i < numZeroesToAdd; i++) {
            sb.insert(0, '0');
        }

        // Add newline character at each row
        for (int j = 8; j < 64; j += 9) {
            sb.insert(j, '\n');
        }

        System.out.println(sb.toString());
    }

    // Prints all piece bitboards
    public void printBitboards() {
        for (int i = 0; i < 12; i++) {
            printBitboard(bitboards[i]);
        }
    }

    // Converts a bit position to its algebraic notation
	public static String toAlgebraicNotation(int position) {
		String result = "";
		result += (char)(104 - (position % 8));
		result += Character.forDigit(position / 8 + 1, 10);
		return result;
	}

	// Converts algebraic representation of a position to its bit position
	public static int toNumericNotation(String position) {
		int col = -1;
		int row = -1;
		for (int i = 0; i < 2; i++) {
			char current = position.charAt(i);
		
			if (Character.isLetter(current)) {
				col = 104 -(int)current;
			} else if (Character.isDigit(current)) {
				row = Character.getNumericValue(current) - 1;
			}
		}

		return col + row * 8;
	}

    // Prints the board
	public void printBoard() {
		long[] bitboards = pieceBitboards();
        String[][] boardString = new String[8][8];
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

        // Print each row
        for (int i = 0; i < 8; i++) {
		    System.out.println(Arrays.toString(boardString[i]));
        }
        System.out.println();
	}
}