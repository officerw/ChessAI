package game;

import java.util.ArrayList;
import java.util.LinkedList;
import boardcomponents.Board;

public class TranspositionTable {
    
    private static int size = (int)Math.pow(2, 20); // The size of the hash table is large to avoid collisions. This will not be increased with use to save time
    private ArrayList<LinkedList<Double>> dataTable; // This is where the evaluation of board states will be stored

    public TranspositionTable() {
        dataTable = new ArrayList<LinkedList<Double>>(size);
        for (int i = 0; i < size; i++) {
            dataTable.add(new LinkedList<Double>());
        }
    }

    // Puts zobrist hash value and the evaluation into the dataTable
    // Since int's are 64 bits, we can only use 32 bits of the hash for indices
    // To control for collisions, we store the zobrist hash as well
    public void put(Board boardState, double evaluation) {
        long hash = boardState.zobristHash();
        int index = (int)(Math.abs(hash) % size);
        LinkedList<Double> list = dataTable.get(index);
        
        if (list == null) {
            // If this index has not been accessed before, create a linked list to store values
            list = new LinkedList<Double>();
            list.add((double)hash);
            list.add(evaluation);
        } else {
            // This list exists, meaning it has been accessed before and contains at least one element
            // Thus, we need to check for conflicts (same zobrist hash values)
            int listIndex = 0;
            boolean conflict = false;
            for (Double value : list) {
                if (listIndex % 2 == 0 && value == (double)hash) { // Even indices are for hash values
                    // There's a conflict when the value is the same as the hash code
                    conflict = true;
                }

                listIndex++;
            }

            // Only add value if there is not a conflict
            if (!conflict) {
                list.add((double)hash);
                list.add(evaluation);
            }
        }
    }

    // Checks if a value with the specified board state has been added
    public boolean contains(Board boardState) {
        long hash = boardState.zobristHash();
        int index = (int)(Math.abs(hash) % size);

        // Given that most lists will either have 0 or 1 elements and dataTable.get(index) is O(1), then it should be constant time access
        LinkedList<Double> list = dataTable.get(index);
        if (list == null || list.isEmpty()) { // List is empty, meaning boardState evaluation not contained
            return false;
        } else {
            int listIndex = 0;
            // Enhanced for loops allow for O(N) access time in linked lists, which is the worst case
            for (Double value : list) { // Check for value in even index that equals the hash
                if (listIndex % 2 == 0 && value == (double)hash) {
                    return true;
                }

                listIndex++;
            }
        }

        return false;
    }

    // Get the evaluation for the given boardState
    // Return null if it is not in the dataTable
    public Double get(Board boardState) {
        if (!contains(boardState)) {
            return null;
        }

        long hash = boardState.zobristHash();
        int index = (int)(Math.abs(hash) % size);

        LinkedList<Double> list = dataTable.get(index);
        boolean returnNextElement = false;
        int listIndex = 0;
        
        for (Double value : list) { // Check for value in even index that equals the hash
            if (listIndex % 2 == 0 && value == (double)hash) {
                returnNextElement = true;
            } else if (returnNextElement) {
                return value;
            }

            listIndex++;
        }

        return null;
    }
}