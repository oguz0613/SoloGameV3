package edu.augustana.csc490.sologamev3;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * Main activity of Pong game.
 */
public class Pong extends Activity {

    private static final int MENU_NEW_GAME = 1;
    private static final int MENU_RESUME = 2;
    private static final int MENU_EXIT = 3;

    private PongThread gameThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pong_layout);

        final PongView pongView = (PongView) findViewById(R.id.main);
        pongView.setStatusView((TextView) findViewById(R.id.status));
        pongView.setScoreView((TextView) findViewById(R.id.score));

        gameThread = pongView.getGameThread();
        if (savedInstanceState == null) {
            gameThread.setState(PongThread.STATE_READY);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameThread.pause();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_NEW_GAME, 0, R.string.menu_new_game);
        menu.add(0, MENU_RESUME, 0, R.string.menu_resume);
        menu.add(0, MENU_EXIT, 0, R.string.menu_exit);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_NEW_GAME:
                gameThread.newGame();
                return true;
            case MENU_EXIT:
                finish();
                return true;
            case MENU_RESUME:
                gameThread.unPause();
                return true;
        }
        return false;
    }

}
