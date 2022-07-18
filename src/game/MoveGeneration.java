package game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import boardcomponents.Board;

public class MoveGeneration {
 
    private static boolean whiteToMove; // Determines whether it's white's turn to move
    private static long illegalWKMoves; // Represents where the white king cannot move due to an attack from a sliding piece
    private static boolean whiteInCheck; // Determines whether white is in check
    private static long attackOnWK; // Represents the attack on the white king; where pieces need to move if they want to capture the attacker or block the attack
    private static long illegalBKMoves; // Represents where the black king cannot move due to an attack from a sliding piece
    private static boolean blackInCheck; // Determines whether black is in check
    private static long attackOnBK; // Represents the attack on the black king; where pieces need to move if they want to capture the attacker or block the attack
    private static long whiteAttacks; // A 64-bit number representing white's attacks
    private static long blackAttacks; // A 64-bit number representing black's attacks
    private static HashMap<String, Long> whiteMoves = new HashMap<String, Long>(); // Store white moves here, where the key is the algebraic representation of a piece's position and the value is the representation of its moves
    private static HashMap<String, Long> blackMoves = new HashMap<String, Long>(); // Store black moves here, where the key is the algebraic representation of a piece's position and the value is the representation of its moves
    private static ArrayList<Long> pins = new ArrayList<Long>(32); // An array of the pins currently on the board 
    private static long defended; // A 64-bit number representing currently defended pieces
    private static long pinnedPieces; // A 64-bit number representing pieces currently pinned pieces
    private static long occupied; // A 64-bit number representing the tiles that are occupied
    private static long occupiedByWhite; // The positions occupied by white
    private static long occupiedByBlack; // The positions occupied by black
    private static long whiteKing; // The white king position
    private static long blackKing; // The black king position
    private static long[] whitePieces = new long[6]; // The 6 bitboards for white pieces
    private static long[] blackPieces = new long[6]; // The 6 bitboards for black pieces

    // Masks for columns
    private static long[] columnMasks = {
        0x0101010101010101L, 0x0202020202020202L, 0x0404040404040404L, 0x0808080808080808L,
        0x1010101010101010L, 0x2020202020202020L, 0x4040404040404040L, 0x8080808080808080L 
    };

    // Masks for rows
    private static long[] rowMasks = {
        0x00000000000000FFL, 0x000000000000FF00L, 0x0000000000FF0000L, 0x00000000FF000000L,
        0x000000FF00000000L, 0x0000FF0000000000L, 0x00FF000000000000L, 0xFF00000000000000L   
    };

    // Masks for diagonals top right to bottom left
    private static long[] diagonalMasksOne = {
        0x1L, 0x102L, 0x10204L, 0x1020408L, 0x102040810L, 0x10204081020L, 0x1020408102040L,
	    0x102040810204080L, 0x204081020408000L, 0x408102040800000L, 0x810204080000000L,
	    0x1020408000000000L, 0x2040800000000000L, 0x4080000000000000L, 0x8000000000000000L
    };

    // Masks for diagonals top left to bottom right
    private static long[] diagonalMasksTwo = {
        0x80L, 0x8040L, 0x804020L, 0x80402010L, 0x8040201008L, 0x804020100804L, 0x80402010080402L,
	    0x8040201008040201L, 0x4020100804020100L, 0x2010080402010000L, 0x1008040201000000L,
	    0x804020100000000L, 0x402010000000000L, 0x201000000000000L, 0x100000000000000L
    };

    // Returns an ArrayList of the legal moves
    public static ArrayList<String> generateMoves(Board board) {
        calculateLegalMoves(board);

        ArrayList<String> moves = new ArrayList<String>();
        HashMap<String, Long> moveSet = whiteToMove ? whiteMoves : blackMoves;
        long occupiedByEnemy = whiteToMove ? occupiedByBlack : occupiedByWhite;
        long attackedByEnemy = whiteToMove ? blackAttacks : whiteAttacks;
        Set<String> pieces = moveSet.keySet();
        char[] promotion = {'q', 'r', 'b', 'n'};

        // Sort generated moves by how good we may expect them to be
        ArrayList<String> captures = new ArrayList<String>();
        ArrayList<String> promotions = new ArrayList<String>();
        ArrayList<String> otherMoves = new ArrayList<String>();
        ArrayList<String> attacked = new ArrayList<String>();

        for (String piecePosition : pieces) {
            long piece = 1L << Board.toNumericNotation(piecePosition);
            ArrayList<Integer> movePositions = bitboardToBitPositions(moveSet.get(piecePosition));
            for (Integer movePosition : movePositions) {
                String currentMove = Board.toAlgebraicNotation(movePosition);
                long move = 1L << movePosition;
                
                // Add promotions
                if ((whitePieces[5] & piece) != 0 && movePosition / 8 == 7) {
                    for (int i = 0; i < 4; i++) {
                        promotions.add(piecePosition + currentMove + promotion[i]);
                    }
                } else if ((blackPieces[5] & piece) != 0 && movePosition / 8 == 0) {
                    for (int i = 0; i < 4; i++) {
                        promotions.add(piecePosition + currentMove + promotion[i]);
                    }
                } else if ((move & occupiedByEnemy) != 0) { // Add captures
                    captures.add(piecePosition + currentMove);
                } else if ((move & attackedByEnemy) != 0) { // Add moves that enter attacked territory
                    attacked.add(piecePosition + currentMove);
                } else { // Add all other moves
                    otherMoves.add(piecePosition + currentMove);
                }
            }
        }

        moves.addAll(captures);
        moves.addAll(promotions);
        moves.addAll(otherMoves);
        moves.addAll(attacked);
        return moves;
    }

    // Returns a set of the legal moves
    public static HashSet<String> legalMoves(Board board) {
        calculateLegalMoves(board);

        HashSet<String> moves = new HashSet<String>();
        HashMap<String, Long> moveSet = whiteToMove ? whiteMoves : blackMoves;
        Set<String> pieces = moveSet.keySet();
        for (String piecePosition : pieces) {
            ArrayList<String> targetPositions = bitboardToAlgebraicMoves(Board.toNumericNotation(piecePosition), moveSet.get(piecePosition));
            for (String targetPosition : targetPositions) {
                moves.add(piecePosition + targetPosition);
            }
        }

        return moves;
    }

    // Updates piece positions
    public static void updatePieceLocations(Board board) {
        long[] bitboards = board.pieceBitboards();

        // Set king positions
        whiteKing = bitboards[0];
        blackKing = bitboards[6];

        for (int i = 0; i < 12; i++) {
            occupied |= bitboards[i];

            if (i < 6) {
                whitePieces[i] |= bitboards[i];
                occupiedByWhite |= bitboards[i];
            } else {
                blackPieces[i - 6] |= bitboards[i];
                occupiedByBlack |= bitboards[i];
            }
        }
    }

    // Resets move generation information
    public static void resetMoveGeneration(Board board) {
        whiteAttacks = 0L;
        blackAttacks = 0L;
        defended = 0L;
        pins.clear();
        pinnedPieces = 0L;
        whiteMoves.clear();
        blackMoves.clear();
        illegalBKMoves = 0L;
        attackOnWK = 0xFFFFFFFFFFFFFFFFL;
        whiteInCheck = false;
        illegalWKMoves = 0L;
        attackOnBK = 0xFFFFFFFFFFFFFFFFL;
        blackInCheck = false;
        whiteToMove = board.whiteToMove();
        occupied = 0L;
        occupiedByBlack = 0L;
        occupiedByWhite = 0L;
        whiteKing = 0L;
        blackKing = 0L;
        whitePieces = new long[6];
        blackPieces = new long[6];
    }

    // Calculates legal moves
    public static void calculateLegalMoves(Board board) {
        calculatePseudoLegalMoves(board);
        removeIllegalMoves(board);
    }

    // Converts pseudolegal moves to legal moves, removing illegal moves
    public static void removeIllegalMoves(Board board) {
        String algebraicWKPosition = Board.toAlgebraicNotation(Long.numberOfTrailingZeros(whiteKing));
        String algebraicBKPosition = Board.toAlgebraicNotation(Long.numberOfTrailingZeros(blackKing));
        long enPassant = board.enPassant();
        long wkMoves = whiteMoves.get(algebraicWKPosition);
        long bkMoves = blackMoves.get(algebraicBKPosition);
        // Remove the ability for a king to move to an attacked position
        wkMoves &= ~blackAttacks;
        bkMoves &= ~whiteAttacks;
        // Update moves
        setMoves(true, algebraicWKPosition, wkMoves);
        setMoves(false, algebraicBKPosition, bkMoves);

        // See if the white king is in check
        if (whiteInCheck) {
            // Prevent the king from moving to a square that would keep the king in check
            wkMoves &= ~illegalWKMoves;
            // Remove the ability to castle
            wkMoves &= ~(whiteKing << 2 | whiteKing >>> 2);
            setMoves(true, algebraicWKPosition, wkMoves);

            // Require other pieces to block the attack or capture the attacker
            Set<String> whitePiecesAlgebraic = whiteMoves.keySet();
            for (String piece : whitePiecesAlgebraic) {
                long piecePosition = 1L << Board.toNumericNotation(piece);
                long currAttackOnWK = whiteMoves.get(piece) & attackOnWK;
                // Remove en passant capture as an option if the piece is not a pawn
                if ((piecePosition & whitePieces[5]) == 0) {
                    currAttackOnWK &= ~enPassant; 
                }

                if (piece.compareTo(algebraicWKPosition) != 0) {
                    setMoves(true, piece, currAttackOnWK);
                }
            }
        } else if (blackInCheck) {
            // Prevent the king from moving to a square that would keep the king in check
            bkMoves &= ~illegalBKMoves;
            // Remove the ability to castle
            bkMoves &= ~(blackKing << 2 | blackKing >>> 2);
            setMoves(false, algebraicBKPosition, bkMoves);

            // Require other pieces to block the attack or capture the attacker
            Set<String> blackPiecesAlgebraic = blackMoves.keySet();
            for (String piece : blackPiecesAlgebraic) {
                long piecePosition = 1L << Board.toNumericNotation(piece);
                long currAttackOnBK = blackMoves.get(piece) & attackOnBK;
                // Remove en passant capture as an option if the piece is not a pawn
                if ((piecePosition & blackPieces[5]) == 0) {
                    currAttackOnBK &= ~enPassant; 
                }
                if (piece.compareTo(algebraicBKPosition) != 0) {
                    setMoves(false, piece, currAttackOnBK);
                }
            }
        }

        // Remove the ability to castle if the tile a king moves through is attacked
        if ((wkMoves & (whiteKing << 2 | whiteKing >>> 2)) != 0) {
            // The right tile is attacked, so we cannot kingside castle
            if ((wkMoves & (whiteKing >>> 1)) == 0) {
                wkMoves &= ~(whiteKing >>> 2);
            }
            // The left tile is attacked, so we cannot queenside castle
            if ((wkMoves & (whiteKing << 1)) == 0) {
                wkMoves &= ~(whiteKing << 2);
            }

            setMoves(true, algebraicWKPosition, wkMoves);
        }
        if ((bkMoves & (blackKing << 2 | blackKing >>> 2)) != 0) {
            // The right tile is attacked, so we cannot kingside castle
            if ((bkMoves & (blackKing >>> 1)) == 0) {
                bkMoves &= ~(blackKing >>> 2);
            }
            // The left tile is attacked, so we cannot queenside castle
            if ((bkMoves & (blackKing << 1)) == 0) {
                bkMoves &= ~(blackKing << 2);
            }

            setMoves(false, algebraicBKPosition, bkMoves);
        }
    }

    // Calculates pseudolegal moves for all pieces
    public static void calculatePseudoLegalMoves(Board board) {
        // Update piece positions and reset move generation information
        resetMoveGeneration(board);
        updatePieceLocations(board);
        
        if (whiteToMove) {
            calculateKingAttacksPins(board, whiteKing);
        } else {
            calculateKingAttacksPins(board, blackKing);
        }

        // Calculate pseudolegal moves for pawns
        long pieces = whitePieces[5] | blackPieces[5];
        ArrayList<Integer> pieceBitPositions = bitboardToBitPositions(pieces);
        for (Integer bitPosition : pieceBitPositions) {
            calculatePseudoLegalPawnAttacks(board, bitPosition);
            calculatePseudoLegalPawnMoves(board, bitPosition);
        }

        // Calculate pseudolegal moves for knights
        pieces =   whitePieces[4] | blackPieces[4];
        pieceBitPositions = bitboardToBitPositions(pieces);
        for (Integer bitPosition : pieceBitPositions) {
            calculatePseudoLegalKnightMoves(bitPosition);
        }

        // Calculate pseudolegal moves for bishops
        pieces = whitePieces[3] | blackPieces[3];
        pieceBitPositions = bitboardToBitPositions(pieces);
        for (Integer bitPosition : pieceBitPositions) {
            calculatePseudoLegalBishopMoves(bitPosition);
        }

        // Calculate pseudolegal moves for rooks
        pieces = whitePieces[2] | blackPieces[2];
        pieceBitPositions = bitboardToBitPositions(pieces);
        for (Integer bitPosition : pieceBitPositions) {
            calculatePseudoLegalRookMoves(bitPosition);
        }
        
        // Calculate pseudolegal moves for queens
        pieces = whitePieces[1] | blackPieces[1];
        pieceBitPositions = bitboardToBitPositions(pieces);
        for (Integer bitPosition : pieceBitPositions) {
            calculatePseudoLegalQueenMoves(bitPosition);
        }

        calculatePseudoLegalKingMoves(board);
    }

    // Calculate pins for pieces of the same color as the provided king
    // Also calculates king attacks from sliding pieces
    public static void calculateKingAttacksPins(Board board, long kingBitboard) {
        long sliders;
        boolean kingIsWhite = kingBitboard == whiteKing;
        int kingBitPosition = Long.numberOfTrailingZeros(kingBitboard);
        
        // Only compute pins and attacks if the king exists
        if (kingBitPosition < 64 && kingBitPosition > -1) {
            if (kingIsWhite) {
                sliders = blackPieces[1] | blackPieces[2] | blackPieces[3] | whiteKing;
            } else {
                sliders = whitePieces[1] | whitePieces[2] | whitePieces[3] | blackKing;
            }

            // Calculate pins for white pieces
            long[] directionalMasks = directionalMasks(kingBitPosition);
            long[] potentialPins = new long[4];
            // Get potential pins for each direction (diagonals, vertical, and horizontal)
            potentialPins[0] = (((sliders & columnMasks[kingBitPosition % 8]) - (2 * kingBitboard)) ^ Long.reverse(Long.reverse(sliders & columnMasks[kingBitPosition % 8]) - 2 * Long.reverse(kingBitboard))) & columnMasks[kingBitPosition % 8];
            potentialPins[1] = ((sliders & diagonalMasksOne[(kingBitPosition / 8) + (kingBitPosition % 8)]) - 2 * kingBitboard) ^ Long.reverse(Long.reverse(sliders & diagonalMasksOne[(kingBitPosition / 8) + (kingBitPosition % 8)]) - 2 * Long.reverse(kingBitboard));
            potentialPins[2] = ((sliders & diagonalMasksTwo[(kingBitPosition / 8) + 7 - (kingBitPosition % 8)]) - (2 * kingBitboard)) ^ Long.reverse(Long.reverse(sliders & diagonalMasksTwo[kingBitPosition / 8 + 7 - (kingBitPosition % 8)]) - (2 * Long.reverse(kingBitboard)));
            potentialPins[3] = ((sliders - 2 * kingBitboard) ^ Long.reverse(Long.reverse(sliders) - 2 * Long.reverse(kingBitboard))) & rowMasks[kingBitPosition / 8];

            long alliedPieces = kingIsWhite ? occupiedByWhite : occupiedByBlack;
            for (int i = 0; i < 8; i += 2) {
                long consideredPins = 0L;
                for (int j = 0; j < 2; j++) {
                    consideredPins = potentialPins[i / 2] & directionalMasks[i + j];
                    int numberOfPotentialPins = Long.bitCount(consideredPins & occupied & ~sliders);
                    // If the number of potential pins is greater than 2, then there is nothing to be considered
                    // Skip to next iteration
                    if (numberOfPotentialPins > 2) {
                        continue;
                    }

                    long enemyPawns = consideredPins & ~alliedPieces & ~sliders & (whitePieces[5] | blackPieces[5]);
                    long alliedPawns = consideredPins & alliedPieces & ~sliders & (whitePieces[5] | blackPieces[5]);

                    // Checks whether the slider is a rook, a bishop, or a queen
                    long enemySliders = consideredPins & ~alliedPieces & sliders;
                    boolean isRook = (enemySliders & (whitePieces[2] | blackPieces[2])) != 0;
                    boolean isQueen = (enemySliders & (whitePieces[1] | blackPieces[1])) != 0;
                    boolean isBishop = (enemySliders & (whitePieces[3] | blackPieces[3])) != 0;
                    boolean canBePinnedOrAttacked = (((isRook || isQueen) && (i == 0 || i == 6)) || ((isQueen || isBishop) && (i == 2 || i == 4)));
                    // If there cannot be a pin or attack in this direction, skip to next iteration
                    if (!canBePinnedOrAttacked) {
                        continue;
                    }

                    // If there are no pieces between the sliding piece and the king, this is an attack
                    if (numberOfPotentialPins == 0) {
                        int illegalDirectionToAdd = j == 1 ? i : i + 1;
                        // Add the attack on the king and the tiles the king cant move to
                        if (kingIsWhite) {
                            whiteInCheck = true;
                            attackOnWK &= consideredPins;
                            illegalWKMoves |= (consideredPins & ~sliders) | kingBitboard | directionalMasks[illegalDirectionToAdd];
                        } else {
                            blackInCheck = true;
                            attackOnBK &= consideredPins;
                            illegalBKMoves |= (consideredPins & ~sliders) | kingBitboard | directionalMasks[illegalDirectionToAdd];
                        }
                        // Move to next direction
                        continue;
                    }

                    // An allied piece can be pinned only if it is the only piece blocking an attack on its king
                    if (numberOfPotentialPins == 1 && Long.bitCount(consideredPins & alliedPieces & ~sliders) == 1) {
                        // Set pin
                        pins.add(consideredPins);
                        pinnedPieces |= consideredPins & alliedPieces; 
                        
                        continue;
                    }

                    long enPassant = board.enPassant();
                    boolean oneEnemyPawnOneAlliedPawn = Long.bitCount(enemyPawns) == 1 && Long.bitCount(alliedPawns) == 1;
                    boolean horizontalDirection = 6 == i;
                    // Ensure that en passant captures do not reveal attacks on the king
                    if (numberOfPotentialPins == 1 && Long.bitCount(enemyPawns) == 1 && ((enemyPawns << 8 | enemyPawns >>> 8) & enPassant) != 0) {
                        // The piece blocking an attack on the king is an enemy pawn that could be captured via en passant, but doing so will reveal an attack on the king
                        board.removeEnPassant();
                        continue;
                    } else if (numberOfPotentialPins == 2 && oneEnemyPawnOneAlliedPawn && horizontalDirection && ((enemyPawns << 8 | enemyPawns >>> 8) & enPassant) != 0) {
                        // The only pieces blocking the attack on the king is one of our pawns and an enemy pawn
                        // Make sure that our pawn cannot en passant capture the enemy pawn next to it, revealing an attack
                        if (Math.abs(Long.numberOfTrailingZeros(enemyPawns) - Long.numberOfTrailingZeros(alliedPawns)) == 1) {
                            board.removeEnPassant();
                        }
                        continue;
                    }
                }
            }
        }
    }

    // Calculates pseudolegal pawn attacks for a pawn at a given bitposition
    public static void calculatePseudoLegalPawnAttacks(Board board, int bitPosition) {
        long pawnPos = 1L << bitPosition;
        String algebraicPosition = Board.toAlgebraicNotation(bitPosition);
        boolean whitePawn = (pawnPos & whitePieces[5]) != 0;
        long allies = whitePawn ? occupiedByWhite : occupiedByBlack;
        long enemyKing = whitePawn ? blackKing : whiteKing;
        long enPassant = board.enPassant();
        boolean capturableViaEnPassant;

        // Checks whether this pawn is capturable via en passant
        if (whitePawn) {
            capturableViaEnPassant = (enPassant << 8 & pawnPos) != 0;
        } else {
            capturableViaEnPassant = (enPassant >>> 8 & pawnPos) != 0;
        }

        // Calculate pawn attacks
        long pawnAttacks = (whitePawn ? (pawnPos << 9) : (pawnPos >>> 7)) & ~columnMasks[0];
        // Check for "left pawn attacks" on the enemy king
        boolean enemyKingAttacked = false;
        if ((pawnAttacks & enemyKing) != 0) {
            enemyKingAttacked = true;
            if (whitePawn && capturableViaEnPassant) {
                attackOnBK &= blackKing >>> 9 | enPassant;
                blackInCheck = true;
            } else if (whitePawn) {
                attackOnBK &= blackKing >>> 9;
                blackInCheck = true;
            } else if (capturableViaEnPassant) {
                attackOnWK &= whiteKing << 7 | enPassant;
                whiteInCheck = true;
            } else {
                attackOnWK &= whiteKing << 7;
                whiteInCheck = true;
            }
        }

        pawnAttacks |= (whitePawn ? (pawnPos << 7) : (pawnPos >>> 9)) & (~columnMasks[7]);
        // Check for "right pawn attacks" on the black king
        if ((pawnAttacks & enemyKing) != 0 && !enemyKingAttacked) {
            if (whitePawn && capturableViaEnPassant) {
                attackOnBK &= blackKing >>> 7 | enPassant;
                blackInCheck = true;
            } else if (whitePawn) {
                attackOnBK &= blackKing >>> 7;
                blackInCheck = true;
            } else if (capturableViaEnPassant) {
                attackOnWK &= whiteKing << 9 | enPassant;
                whiteInCheck = true;
            } else {
                attackOnWK &= whiteKing << 9;
                whiteInCheck = true;
            }
        }

        // Add defended positions
        defended |= pawnAttacks & allies;

        // Add attacks to the respective player
        if (whitePawn) {
            whiteAttacks |= pawnAttacks;
        } else {
            blackAttacks |= pawnAttacks;
        }

        pawnAttacks &= ~allies;

        // Add en passant moves
        int enPassantRow = whitePawn ? 5 : 2;
        
        pawnAttacks &= ((~allies & occupied) | (enPassant & rowMasks[enPassantRow]));

        // Restrict moves to pinned moves, if necessary
        pawnAttacks &= pinnedMoveSet(bitPosition);

        // Add moves to respective player and piece
        updateMoves(whitePawn, algebraicPosition, pawnAttacks);
    }

    // Calculates pseudolegal pawn moves
    public static void calculatePseudoLegalPawnMoves(Board board, int bitPosition) {
        long firstMove = board.firstMoves();
        long pawnPos = 1L << bitPosition;
        boolean whitePawn = (pawnPos & occupiedByWhite) != 0;
        String algebraicPosition = Board.toAlgebraicNotation(bitPosition);

        // Calculate pawn moves
        long pawnMoves = (whitePawn ? (pawnPos << 8) : (pawnPos >>> 8)) & (~occupied);
        if (whitePawn && pawnMoves != 0) {
            pawnMoves |= (pawnPos << 16) & (~occupied) & (firstMove << 16);
        } else if (pawnMoves != 0) {
            pawnMoves |= (pawnPos >>> 16) & (~occupied) & (firstMove >>> 16);
        }

        // Restrict moves to pinned moves, if necessary
        pawnMoves &= pinnedMoveSet(bitPosition);

        // Add moves to respective player and piece
        updateMoves(whitePawn, algebraicPosition, pawnMoves);
    }

    // Calculates pseudolegal knight attacks for a knight at a given bit position
    public static void calculatePseudoLegalKnightMoves(int bitPosition) {
        long knightPos = 1L << bitPosition;
        boolean whiteKnight = (occupiedByWhite & knightPos) != 0;
        String algebraicPosition = Board.toAlgebraicNotation(bitPosition);
        long rightTwoColumns = columnMasks[0] | columnMasks[1];
        long leftTwoColumns = columnMasks[7] | columnMasks[6];
        long alliedPieces = whiteKnight ? occupiedByWhite : occupiedByBlack;

        // Calculate knight moves
        long knightMoves = ((knightPos << 17 & ~columnMasks[0]) | (knightPos << 10 & ~rightTwoColumns) | (knightPos << 15 & ~columnMasks[7]) | 
                            (knightPos << 6 & ~leftTwoColumns) | (knightPos >>> 17 & ~columnMasks[7]) | (knightPos >>> 10 & ~leftTwoColumns) |
                            (knightPos >>> 15 & ~columnMasks[0]) | (knightPos >>> 6 & ~rightTwoColumns));
        defended |= knightMoves & alliedPieces;
        knightMoves &= ~alliedPieces;

        // Determine if the knight attacks the enemy king
        if (whiteKnight && (knightMoves & blackKing) != 0) {
            attackOnBK &= knightPos;
            blackInCheck = true;
        } else if ((knightMoves & whiteKing) != 0) {
            attackOnWK &= knightPos;
            whiteInCheck = true;
        }

        // Restrict moves to pinned move set, if necessary
        knightMoves &= pinnedMoveSet(bitPosition);

        // Add attacks
        if (whiteKnight) {
            whiteAttacks |= knightMoves;
        } else {
            blackAttacks |= knightMoves;
        }

        // Add moves to respective player and piece
        updateMoves(whiteKnight, algebraicPosition, knightMoves);
    }

    // Calculates pseudolegal rook moves for a rook at a given bit position
    public static void calculatePseudoLegalRookMoves(int bitPosition) {
        long rookPos = 1L << bitPosition;
        long horizontalMoves = ((occupied - 2 * rookPos) ^ Long.reverse(Long.reverse(occupied) - 2 * Long.reverse(rookPos))) & rowMasks[bitPosition / 8];
        long verticalMoves = (((occupied & columnMasks[bitPosition % 8]) - (2 * rookPos)) ^ Long.reverse(Long.reverse(occupied & columnMasks[bitPosition % 8]) - 2 * Long.reverse(rookPos))) & columnMasks[bitPosition % 8];
        boolean whiteRook = (rookPos & occupiedByWhite) != 0;
        String algebraicPosition = Board.toAlgebraicNotation(bitPosition);
        long rookMoves;

        // Differentiate between white and black rook attacks
        if (whiteRook) {
            defended |= (horizontalMoves & occupiedByWhite) | (verticalMoves & occupiedByWhite);
            rookMoves = (horizontalMoves | verticalMoves) & ~occupiedByWhite;
        } else {
            defended |= (horizontalMoves & occupiedByBlack) | (verticalMoves & occupiedByBlack);
            rookMoves = (horizontalMoves | verticalMoves) & ~occupiedByBlack;
        }

        // Restrict moves to pinned move set, if necessary
        rookMoves &= pinnedMoveSet(bitPosition);

        // Add attacks
        if (whiteRook) {
            whiteAttacks |= rookMoves;
        } else {
            blackAttacks |= rookMoves;
        }

        // Add moves to respective player and piece
        updateMoves(whiteRook, algebraicPosition, rookMoves);
    }

    // Calculates pseudolegal bishop moves for a rook at a given bit position
    public static void calculatePseudoLegalBishopMoves(int bitPosition) {
        long bishopPos = 1L << bitPosition;
        boolean whiteBishop = (bishopPos & occupiedByWhite) != 0;
        String algebraicPosition = Board.toAlgebraicNotation(bitPosition);
        long diagonalOne = ((occupied & diagonalMasksOne[bitPosition / 8 + bitPosition % 8]) - 2 * bishopPos) ^ Long.reverse(Long.reverse(occupied & diagonalMasksOne[bitPosition / 8 + bitPosition % 8]) - 2 * Long.reverse(bishopPos));
        long diagonalTwo = ((occupied & diagonalMasksTwo[bitPosition / 8 + 7 - bitPosition % 8]) - (2 * bishopPos)) ^ Long.reverse(Long.reverse(occupied & diagonalMasksTwo[bitPosition / 8 + 7 - bitPosition % 8]) - (2 * Long.reverse(bishopPos)));
        long bishopMoves;

        // Ensure moves are limited to diagonals
        diagonalOne &= diagonalMasksOne[bitPosition / 8 + bitPosition % 8];
        diagonalTwo &= diagonalMasksTwo[bitPosition / 8 + 7 - bitPosition % 8];

        // Differentiate between white and black bishop attacks
        if ((bishopPos & occupiedByWhite) != 0) {
            defended |= (diagonalOne & occupiedByWhite) | (diagonalTwo & occupiedByWhite);
            bishopMoves = (diagonalOne | diagonalTwo) & ~occupiedByWhite;
        } else {
            defended |= (diagonalOne & occupiedByBlack) | (diagonalTwo & occupiedByBlack);
            bishopMoves = (diagonalOne | diagonalTwo) & ~occupiedByBlack;
        }

        // Restrict moves to pinned move set, if necessary
        bishopMoves &= pinnedMoveSet(bitPosition);

        // Add attacks
        if (whiteBishop) {
            whiteAttacks |= bishopMoves;
        } else {
            blackAttacks |= bishopMoves;
        }

        // Add moves to respective player and piece
        updateMoves(whiteBishop, algebraicPosition, bishopMoves);
    }

    // Calculates pseudolegal queen moves for a queen at a given bit position
    public static void calculatePseudoLegalQueenMoves(int bitPosition) {
        calculatePseudoLegalBishopMoves(bitPosition);
        calculatePseudoLegalRookMoves(bitPosition);
    }

    // Calculates pseudolegal king moves for a king at a given bit position
    // Assumes pseudolegal moves for all other pieces have been calculated previously
    public static void calculatePseudoLegalKingMoves(Board board) {
        // Calculate white king moves
        String whiteAlgebraicPosition = Board.toAlgebraicNotation(Long.numberOfTrailingZeros(whiteKing));
        long whiteKingMoves = ((whitePieces[0] >>> 9 & ~columnMasks[7]) | whitePieces[0] >>> 8 | (whitePieces[0] >>> 7 & ~columnMasks[0]) | (whitePieces[0] >>> 1 & ~columnMasks[7]) |
                               (whitePieces[0] << 1 & ~columnMasks[0]) | (whitePieces[0] << 9 & ~columnMasks[0]) | whitePieces[0] << 8 | (whitePieces[0] << 7 & ~columnMasks[7]));
        defended |= whiteKingMoves & occupiedByWhite;
        whiteKingMoves &= ~occupiedByWhite;
        
        // Calculate black king moves
        String blackAlgebraicPosition = Board.toAlgebraicNotation(Long.numberOfTrailingZeros(blackKing));
        long blackKingMoves = ((blackPieces[0] >>> 9 & ~columnMasks[7]) | blackPieces[0] >>> 8 | (blackPieces[0] >>> 7 & ~columnMasks[0]) | (blackPieces[0] >>> 1 & ~columnMasks[7]) |
                               (blackPieces[0] << 1 & ~columnMasks[0]) | (blackPieces[0] << 9 & ~columnMasks[0]) | blackPieces[0] << 8 | (blackPieces[0] << 7 & ~columnMasks[7]));
        defended |= blackKingMoves & occupiedByBlack;
        blackKingMoves &= ~occupiedByBlack;

        // Remove defended moves
        blackKingMoves &= ~defended;
        whiteKingMoves &= ~defended;

        // Add attacks
        whiteAttacks |= whiteKingMoves;
        blackAttacks |= blackKingMoves;

        long firstMoves = board.firstMoves();
        long castlingRooks = (whitePieces[2] | blackPieces[2]) & firstMoves;
        // Add castling for the white king
        if ((firstMoves & whiteKing) != 0) {
            // Check for a queen's side castle
            if ((((whiteKing >>> 3) & castlingRooks) != 0) && (occupied & (whiteKing >>> 2 | whiteKing >>> 1)) == 0) {
                whiteKingMoves |= whiteKing >>> 2;
            }
            // Check for a king's side castle
            if ((((whiteKing << 4) & castlingRooks) != 0) && (occupied & (whiteKing << 3 | whiteKing << 2 | whiteKing << 1)) == 0) {
                whiteKingMoves |= whiteKing << 2;
            }
        }
        // Add castling for the black king
        if ((firstMoves & blackKing) != 0) {
            // Check for a queen's side castle
            if ((((blackKing >>> 3) & castlingRooks) != 0) && (occupied & (blackKing >>> 2 | blackKing >>> 1)) == 0) {
                blackKingMoves |= blackKing >>> 2;
            }
            // Check for a king's side castle
            if ((((blackKing << 4) & castlingRooks) != 0) && (occupied & (blackKing << 3 | blackKing << 2 | blackKing << 1)) == 0) {
                blackKingMoves |= blackKing << 2;
            }
        }

        // Add moves to respective player
        updateMoves(true, whiteAlgebraicPosition, whiteKingMoves);
        updateMoves(false, blackAlgebraicPosition, blackKingMoves);
    }

    // Update moves
    public static void updateMoves(boolean updatingWhite, String algebraicPosition, long moves) {
        if (updatingWhite) {
            Long value = whiteMoves.get(algebraicPosition);
            long currentValue = value == null ? 0L : value;
            whiteMoves.put(algebraicPosition, currentValue | moves);
        } else {
            Long value = blackMoves.get(algebraicPosition);
            long currentValue = value == null ? 0L : value;
            blackMoves.put(algebraicPosition, currentValue | moves);
        }
    }

    // Sets moves
    public static void setMoves(boolean settingWhite, String algebraicPosition, long moves) {
        if (settingWhite) {
            whiteMoves.put(algebraicPosition, moves);
        } else {
            blackMoves.put(algebraicPosition, moves);
        }
    }

    // Returns the pinned moveset for the piece at the provided bit position
    // 0xFFF...FFF is returned if there is no pin because the piece is not restricted
    public static long pinnedMoveSet(int bitPosition) {
        long piecePos = 1L << bitPosition;

        if ((piecePos & pinnedPieces) != 0) {
            for (Long pin : pins) {
                if ((pin & piecePos) != 0) {
                    return pin;
                }
            }
        }

        return 0xFFFFFFFFFFFFFFFFL;
    }

    // Returns an array of masks originating from a bit position and goes in each sliding direction
    // The order of directions is up, down, top right, bottom left, top left, bottom right, right, left
    public static long[] directionalMasks(int bitPosition) {
        long[] masks = new long[8];

        if (bitPosition == 64) {
            return masks;
        }

        // A mask containing all rows north of the bitposition
        long northMask = 0;
        for (int row = bitPosition / 8 + 1; row < 8; row++) {
            northMask |= rowMasks[row];
        }
        // A mask containing all columns east of the bitposition
        long eastMask = 0;
        for (int col = bitPosition % 8 - 1; col >= 0; col--) {
            eastMask |= columnMasks[col];
        }
        // A mask containing all rows south of the bitposition
        long southMask = 0;
        for (int row = bitPosition / 8 - 1; row >= 0; row--) {
            southMask |= rowMasks[row];
        }
        // A mask containing all columns west of the bitposition
        long westMask = 0;
        for (int col = bitPosition % 8 + 1; col < 8; col++) {
            westMask |= columnMasks[col];
        }

        // Set directional masks
        int bitRow = bitPosition / 8;
        int bitCol = bitPosition % 8;
        int bitDiagonalOne = bitPosition / 8 + bitPosition % 8;
        int bitDiagonalTwo = bitPosition / 8 + 7 - bitPosition % 8;

        masks[0] = columnMasks[bitCol] & northMask;
        masks[1] = columnMasks[bitCol] & southMask;
        masks[2] = diagonalMasksOne[bitDiagonalOne] & northMask & eastMask;
        masks[3] = diagonalMasksOne[bitDiagonalOne] & southMask & westMask;
        masks[4] = diagonalMasksTwo[bitDiagonalTwo] & northMask & westMask;
        masks[5] = diagonalMasksTwo[bitDiagonalTwo] & southMask & eastMask;
        masks[6] = rowMasks[bitRow] & westMask;
        masks[7] = rowMasks[bitRow] & eastMask;

        return masks;
    }

    // Returns whether the move targets a king
    public static boolean capturesKing(String move) {
        long target = 1L << Board.toNumericNotation(move.substring(2, 4));
        return (target & (whiteKing | blackKing)) != 0;
    }

    // Returns whether the current player to move is in check
    public static boolean inCheck(boolean whiteToMove) {
        return whiteToMove ? whiteInCheck : blackInCheck;
    }

    // Returns currently attacked tiles
    public static long[] attacks() {
        long[] attacks = {whiteAttacks, blackAttacks};
        return attacks;
    }

    // Returns currently occupied tiles
    public static long[] occupied() {
        long[] occupiedByColor = new long[2];

        occupiedByColor[0] = occupiedByWhite;
        occupiedByColor[1] = occupiedByBlack;

        return occupiedByColor;
    }

    // Returns currently defended tiles
    public static long[] defended() {
        long[] defendedByColor = new long[2];

        defendedByColor[0] = defended & occupiedByWhite;
        defendedByColor[1] = defended & occupiedByBlack;

        return defendedByColor;
    }

    // Returns an ArrayList of Strings, converting the positions on a bitboard to positions in algebraic notation
    public static ArrayList<String> bitboardToAlgebraicMoves(int piece, long moveBitboard) {
        ArrayList<Integer> bitPositions = bitboardToBitPositions(moveBitboard);
        ArrayList<String> algebraicPositions = new ArrayList<String>();
        char[] promotion = {'q', 'r', 'b', 'n'};

        for (Integer bitPosition : bitPositions) {
            String currentMove = Board.toAlgebraicNotation(bitPosition);
            // Check if a pawn reaches the end of the board
            // If so, add the four promotion options to the move
            if ((whitePieces[5] & (1L << piece)) != 0 && bitPosition / 8 == 7) {
                for (int i = 0; i < 4; i++) {
                    algebraicPositions.add(currentMove + promotion[i]);
                }
            } else if ((blackPieces[5] & (1L << piece)) != 0 && bitPosition / 8 == 0) {
                for (int i = 0; i < 4; i++) {
                    algebraicPositions.add(currentMove + promotion[i]);
                }
            } else {
                algebraicPositions.add(currentMove);
            }
        }

        return algebraicPositions;
    }

    // Returns an array of bit positions from a bitboard of pieces
    public static ArrayList<Integer> bitboardToBitPositions(long bitboard) {
        int numPieces = Long.bitCount(bitboard);
        ArrayList<Integer> bitPositions = new ArrayList<Integer>();

        for (int i = 0; i < numPieces; i++) {
            long currPiece = Long.lowestOneBit(bitboard);
            bitPositions.add(Long.numberOfTrailingZeros(currPiece));
            bitboard ^= currPiece;
        }

        return bitPositions;
    }
}
