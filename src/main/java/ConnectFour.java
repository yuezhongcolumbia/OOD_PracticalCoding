import jdk.swing.interop.DropTargetContextWrapper;

//class Game:
//        - board: Board
//    - player1: Player
//    - player2: Player
//    - currentPlayer: Player
//    - state: GameState        // IN_PROGRESS, WON, DRAW
//    - winner: Player?
//
//        + Game(player1, player2)
//    + makeMove(player, column) -> bool
//    + getCurrentPlayer() -> Player
//    + getGameState() -> GameState
//    + getWinner() -> Player?
//        + getBoard() -> Board
//
//class Board:
//        - rows: int = 6
//        - cols: int = 7
//        - grid: DiscColor?[rows][cols]
//
//        + Board()
//    + getRows() -> int
//    + getCols() -> int
//    + canPlace(column) -> bool
//    + placeDisc(column, color) -> int
//    + isFull() -> bool
//    + checkWin(row, column, color) -> bool
//    + getCell(row, column) -> DiscColor?
//
//class Player:
//        - name: string
//    - color: DiscColor
//
//    + Player(name, color)
//    + getName() -> string
//    + getColor() -> DiscColor
//
//enum GameState:
//IN_PROGRESS
//        WON
//DRAW
//
//enum DiscColor:
//RED
//        YELLOW



public class ConnectFour {
    public static void main(String[] args) {
        Player john = new Player("John", DiscColor.YELLOW);
        Player jane = new Player("Jane", DiscColor.RED);
        Board board = new Board();
        Game game = new Game(john, jane, board);
        game.makeMove(john, 0);
        game.makeMove(jane, 2);
        game.makeMove(john, 0);
        game.makeMove(jane, 2);
        game.makeMove(john, 0);
        game.makeMove(jane, 2);
        game.makeMove(john, 0);
        game.makeMove(jane, 2);
    }





}

class Game{
//        - board: Board
//    - player1: Player
//    - player2: Player
//    - currentPlayer: Player
//    - state: GameState        // IN_PROGRESS, WON, DRAW
//    - winner: Player?
    private final Board board;
    private final Player player1;
    private final Player player2;
    private Player currentPlayer;
    private Player winner;
    private GameState state;

    public Game(Player player1, Player player2, Board board){
        this.player1 = player1;
        this.player2 = player2;
        this.board = board;
        this.state = GameState.IN_PROGRESS;
        this.winner = null;
        currentPlayer = player1;
    }
    public Player getCurrentPlayer(){
        return currentPlayer;
    }

    public boolean makeMove(Player player, int col){
        //check game state
        //get current player
        //make a move on the board
        //update game state
        if (state != GameState.IN_PROGRESS){
            System.out.println("The game is already over");
            return false;
        }
        if (currentPlayer != player){
            return false;
        }
        int row = board.placeDisc(player.getDiscColor(), col);
        if (row == -1){
            return false;
        }
        if (board.checkWin(row, col, player.getDiscColor())){
            winner = player;
            state = GameState.WON;
            System.out.printf("We have a winner, [%s], and the game is over%n"
                    , winner.getPlayerName());
        } else if (board.isFull()){
            state = GameState.DRAW;
        } else {
            currentPlayer = player == player2 ? player1 : player2;
        }
        return true;
    }



}
class Board{
    //        - rows: int = 6
//        - cols: int = 7
//        - grid: DiscColor?[rows][cols]
//
//        + Board()
//    + getRows() -> int
//    + getCols() -> int
//    + canPlace(column) -> bool
//    + placeDisc(column, color) -> int
//    + isFull() -> bool
//    + checkWin(row, column, color) -> bool
//    + getCell(row, column) -> DiscColor?

    private final int rows = 6;
    private final int cols = 7;
    private final DiscColor[][] grid = new DiscColor[rows][cols];

    public Board(){

    }

    public int placeDisc(DiscColor discColor, int col){
        int res = -1;
        if (!canPlace(col)) return res;
        for(int i = rows - 1; i >= 0; i--){
            if (grid[i][col] == null){
                grid[i][col] = discColor;
                res = i;
                break;
            }
        }
        return res;
    }

    public boolean canPlace(int column){
        if (column < 0 || column >= cols){
            return false;
        }
        return grid[0][column] == null;
    }

    public boolean checkWin(int row, int col, DiscColor color){
        int[][] directions = {{1,0},{0,1},{-1,1},{1,1}};
        for(int[] dir: directions){
            int count = 1;
            count += countInDirection(dir[0],dir[1], color, row, col);
            count += countInDirection(-dir[0],-dir[1], color, row, col);
            if (count >= 4) {
                return true;
            }
        }
        return false;
    }

    public int countInDirection(int dr, int dc, DiscColor color, int r, int c){
        r += dr;
        c += dc;
        int count = 0;
        while(r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] == color){
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    public boolean isFull(){
        for(int i = 0; i < cols; i++){
            if (grid[0][i] == null){
                return false;
            }
        }
        return true;
    }




}

enum DiscColor{
    RED,
    YELLOW
}

enum GameState{
    WON,
    DRAW,
    IN_PROGRESS
}
class Player{
    String playerName;
    DiscColor discColor;

    public Player(String playerName, DiscColor discColor){
        this.playerName = playerName;
        this.discColor = discColor;
    }
    public String getPlayerName(){
        return playerName;
    }
    public DiscColor getDiscColor(){
        return discColor;
    }
}
