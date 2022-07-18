package boardcomponents;

public class BoardState {
    
	private int pieceCount; // The number of pieces for the given board state
    private long[] bitboards = new long[12]; // The 12 piece bitboards for the given board state
    private long firstMoves; // The pieces that have not moved before for the given board state
    private long enPassant; // Stores the location of where pawns can en passant capture for the given board state
    private boolean whiteToMove; // Stores whether it is currently white's turn to move for the given board state
    private double currentEval; // Stores the current board evaluation

    public BoardState(Board boardToSave) {
        pieceCount = boardToSave.pieceCount();
        firstMoves = boardToSave.firstMoves();
        enPassant = boardToSave.enPassant();
        whiteToMove = boardToSave.whiteToMove();
        currentEval = boardToSave.currentEval();
        
        // Deep copy the bitboards
        long[] bitboardsToCopy = boardToSave.pieceBitboards();
        for (int i = 0; i < 12; i++) {
            bitboards[i] = bitboardsToCopy[i];
        }
    }

    // Restores the given board to the board state stored in this object
    public void restore(Board boardToRestore) {
        boardToRestore.restore(pieceCount, bitboards, firstMoves, enPassant, whiteToMove, currentEval);
    }
}
