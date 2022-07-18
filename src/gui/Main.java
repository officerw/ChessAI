package gui;

import java.util.Scanner;
import game.Game;

public class Main {
    public static void main(String[] args) {
        System.out.println("Welcome to a Chess Engine and AI by William Officer.");
        System.out.println("To begin playing, enter 'start'. To exit at any time, enter 'exit'.");

        Scanner keyboard = new Scanner(System.in);
        String input = null;
        boolean play = true;
        boolean beganGame = false;
        boolean playAsWhite = true;
        Game chess = null;

        while (play) {
            try {
                if (!beganGame) {
                    input = keyboard.nextLine();
                } else {
                    input = "waitForMove";
                }
            } catch (Exception e) {
                System.err.println("Failed to accept input");
            }

            if (input.compareTo("exit") == 0 && !beganGame) {
                play = false;
                break;
            } else if (input.compareTo("start") == 0 && !beganGame) {
                System.out.println("Enter a FEN String. If you simply want the default chess setup, hit enter.");
                input = keyboard.nextLine();
                System.out.println("Do you want to play as white? Enter 'yes' if so. Otherwise, enter 'no'.");
                playAsWhite = keyboard.nextLine().compareTo("yes") == 0;
                // Create the game
                if (input.compareTo("") == 0) {
                    chess = new Game(playAsWhite);
                } else {
                    chess = new Game(input, playAsWhite);
                }

                beganGame = true;
                System.out.println("To enter a move, type it in simple algebraic notation. Example: 'g1f3' moves a piece from g1 to f3.");
                System.out.println("Note: If a move is illegal, it will not be played, and you will be prompted for another.");
                System.out.println();
                System.out.println("Here is the current board configuration.");
                chess.printBoard(playAsWhite);
            }

            if (beganGame && playAsWhite) {
                if (chess.checkmate(true) && chess.checkmate(false)) {
                    System.out.println("Stalemate! The game is over.");
                    play = false;
                    break;
                } else if (chess.checkmate(true)) {
                    System.out.println("You have been checkmated! You lost.");
                }

                while (true) { // Prompt player for a legal move until they provide a legal move
                    System.out.println("Enter a legal move in simple algebraic form.");
                    input = keyboard.nextLine();

                    if (chess.makeMove(input)) {
                        System.out.println("Here is the new board configuration:");
                        chess.printBoardWhitePerspective();
                        break;
                    }

                    System.out.println("The move you entered was illegal. Enter another move.");
                }

                if (!chess.checkmate(false)) {
                    System.out.println("The AI's response was " + chess.aiMove() + ".");
                    chess.printBoardWhitePerspective();
                } else {
                    System.out.println("You checkmated the AI! Congratulations!");
                    play = false;
                    break;
                }
            } else if (beganGame) {
                if (!chess.checkmate(true)) {
                    System.out.println("The AI's response was " + chess.aiMove() + ".");
                    chess.printBoardBlackPerspective();
                } else {
                    System.out.println("You checkmated the AI! Congratulations!");
                    play = false;
                    break;
                }

                if (chess.checkmate(true) && chess.checkmate(false)) {
                    System.out.println("Stalemate! The game is over.");
                    play = false;
                    break;
                } else if (chess.checkmate(false)) {
                    System.out.println("You have been checkmated! You lost.");
                }

                while (true) { // Prompt player for a legal move until they provide a legal move
                    System.out.println("Enter a legal move in simple algebraic form.");
                    input = keyboard.nextLine();
                    if (chess.makeMove(input)) {
                        System.out.println("Here is the new board configuration:");
                        chess.printBoardBlackPerspective();
                        break;
                    }

                    System.out.println("The move you entered was illegal. Enter another move.");
                }
            }
        }        

        if (keyboard != null) {
            keyboard.close();
        }
    }
}
