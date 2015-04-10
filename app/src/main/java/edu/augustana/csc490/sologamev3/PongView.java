package edu.augustana.csc490.sologamev3;

        import android.content.Context;
        import android.os.Handler;
        import android.os.Message;
        import android.util.AttributeSet;
        import android.view.MotionEvent;
        import android.view.SurfaceHolder;
        import android.view.SurfaceView;
        import android.widget.TextView;

/**
 * A simple Pong game.
 */
public class PongView extends SurfaceView implements SurfaceHolder.Callback {

    /**
     * The game thread that actually draws the animation and handles user input.
     */
    private PongThread gameThread;

    /**
     * Text view to display game status (Win, Lose, Paused etc.).
     */
    private TextView statusView;

    /**
     * Text view to display game score.
     */
    private TextView scoreView;

    public PongView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        gameThread = new PongThread(holder, context,
                new Handler() {
                    @Override
                    public void handleMessage(Message m) {
                        //statusView.setVisibility(m.getData().getInt("vis"));
                        //statusView.setText(m.getData().getString("text"));
                    }
                },
                new Handler() {
                    @Override
                    public void handleMessage(Message m) {
                        //scoreView.setText(m.getData().getString("text"));
                    }
                },
                attributeSet
        );

        setFocusable(true);
    }

    /**
     * @param textView to be used for status messages
     */
    public void setStatusView(TextView textView) {
        statusView = textView;
    }

    /**
     * @param textView to be used to display score
     */
    public void setScoreView(TextView textView) {
        scoreView = textView;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) {
            gameThread.pause();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        gameThread.setSurfaceSize(width, height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        gameThread.setRunning(true);
        gameThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        gameThread.setRunning(false);
        while (retry) {
            try {
                gameThread.join();
                retry = false;
            } catch (InterruptedException e) {
                // don't care
            }
        }
    }

    private boolean moving;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (gameThread.betweenRounds()) {
                    // resume game
                    gameThread.setState(PongThread.STATE_RUNNING);
                } else {
                    if (gameThread.touchOnHumanPaddle(event)) {
                        moving = true;
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (moving) {
                    gameThread.humanPaddleHandler(event);
                }
                return true;
            case MotionEvent.ACTION_UP:
                moving = false;
            default:
                return true;
        }
    }

    public PongThread getGameThread() {
        return gameThread;
    }

}
