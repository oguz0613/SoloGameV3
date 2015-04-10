package edu.augustana.csc490.sologamev3;


        import android.content.Context;
        import android.content.res.Resources;
        import android.content.res.TypedArray;
        import android.graphics.Canvas;
        import android.graphics.Color;
        import android.graphics.DashPathEffect;
        import android.graphics.Paint;
        import android.os.Bundle;
        import android.os.Handler;
        import android.os.Message;
        import android.os.SystemClock;
        import android.util.AttributeSet;
        import android.util.Log;
        import android.view.MotionEvent;
        import android.view.SurfaceHolder;
        import android.view.View;
        import android.widget.TextView;

        import java.util.Random;

/**
 * Handle animation, game logic and user input.
 */
public class PongThread extends Thread {

    /*
     * State related constants
     */
    public static final int STATE_PAUSE = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_LOSE = 3;
    public static final int STATE_WIN = 4;

    /*
     * Physics constants
     */
    private static final int BALL_SPEED = 12;
    private static final int PADDLE_SPEED = 12;
    private static final int FPS = 60;
    private static final double MAX_BOUNCE_ANGLE = 5 * Math.PI / 12; // 75 degrees in radians
    private static final int COLLISION_FRAMES = 5;

    /*
    * Constants used when game state is saved/restored
    */
    private static final String HUMAN_PLAYER_DATA = "humanPlayer";
    private static final String COMPUTER_PLAYER_DATA = "computerPlayer";
    private static final String BALL_DATA = "ball";
    private Boolean possesion = true;

    private static final String TAG = "PongThread";

    /**
     * Handle to the surface manager object we interact with
     */
    private final SurfaceHolder surfaceHolder;

    /**
     * Message handler used by thread to interact with status TextView
     */
    private final Handler statusHandler;

    /**
     * Message handler used by thread to interact with score TextView
     */
    private final Handler scoreHandler;

    /**
     * Handle to the application context
     */
    private final Context context;

    /**
     * Indicate whether the surface has been created & is ready to draw
     */
    private boolean run = false;

    /**
     * The state of the game.
     */
    private int state;

    /*
     * Game objects
     */
    private Player humanPlayer;
    private Player computerPlayer;
    private Ball ball;
    private PointGate pointGate;

    /**
     * Median line paint style.
     */
    private Paint medianLinePaint;

    /**
     * Canvas bounds paint.
     */
    private Paint canvasBoundsPaint;

    /**
     * Current height of the canvas.
     */
    private int canvasHeight = 1;

    /**
     * Current width of the canvas.
     */
    private int canvasWidth = 1;

    /**
     * Used to make computer to "forget" to move the paddle in order to behave more like a human opponent.
     */
    private Random randomGen;

    /**
     * The probability to move computer paddle.
     */
    private float computerMoveProbability;


    PongThread(final SurfaceHolder surfaceHolder,
        final Context context,
        final Handler statusHandler,
        final Handler scoreHandler,
        final AttributeSet attributeSet) {
        this.surfaceHolder = surfaceHolder;
        this.statusHandler = statusHandler;
        this.scoreHandler = scoreHandler;
        this.context = context;

        TypedArray a = context.obtainStyledAttributes(attributeSet, R.styleable.PongView);

        int paddleHeight = a.getInt(R.styleable.PongView_paddleHeight, 175);
        int paddleWidth = a.getInt(R.styleable.PongView_paddleWidth, 40);
        int ballRadius = a.getInt(R.styleable.PongView_ballRadius, 15);
        int gateHeight = a.getInt(R.styleable.PongView_gateHeight, 75);
        int gateWidth = a.getInt(R.styleable.PongView_gateWidth, 25);

        a.recycle();

        Paint humanPlayerPaint = new Paint();
        humanPlayerPaint.setAntiAlias(true);
        humanPlayerPaint.setColor(Color.BLUE);

        humanPlayer = new Player(paddleWidth, paddleHeight, humanPlayerPaint);

        Paint computerPlayerPaint = new Paint();
        computerPlayerPaint.setAntiAlias(true);
        computerPlayerPaint.setColor(Color.RED);

        computerPlayer = new Player(paddleWidth, paddleHeight, computerPlayerPaint);

        Paint ballPaint = new Paint();
        ballPaint.setAntiAlias(true);
        ballPaint.setColor(Color.GREEN);

        ball = new Ball(ballRadius, ballPaint);

        Paint gatePaint = new Paint();
        gatePaint.setAntiAlias(true);
        gatePaint.setColor(Color.YELLOW);
        pointGate = new PointGate(gateWidth, gateHeight, gatePaint);

        medianLinePaint = new Paint();
        medianLinePaint.setAntiAlias(true);
        medianLinePaint.setColor(Color.YELLOW);
        medianLinePaint.setAlpha(80);
        medianLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        medianLinePaint.setStrokeWidth(2.0f);
        medianLinePaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));

        canvasBoundsPaint = new Paint();
        canvasBoundsPaint.setAntiAlias(true);
        canvasBoundsPaint.setColor(Color.YELLOW);
        canvasBoundsPaint.setStyle(Paint.Style.STROKE);
        canvasBoundsPaint.setStrokeWidth(1.0f);

        randomGen = new Random();
        computerMoveProbability = 0.85f;
    }

    /**
     * Game loop.
     */
    @Override
    public void run() {
        long mNextGameTick = SystemClock.uptimeMillis();
        int skipTicks = 1000 / FPS;
        while (run) {
            Canvas c = null;
            try {
                c = surfaceHolder.lockCanvas(null);
                if (c != null) {
                    synchronized (surfaceHolder) {
                        if (state == STATE_RUNNING) {
                            updatePhysics();
                        }
                        updateDisplay(c);
                    }
                }
            } finally {
                if (c != null) {
                    surfaceHolder.unlockCanvasAndPost(c);
                }
            }
            mNextGameTick += skipTicks;
            long sleepTime = mNextGameTick - SystemClock.uptimeMillis();
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted", e);
                }
            }
        }
    }

    /**
     * Used to signal game thread whether it should be running or not.
     */
    void setRunning(boolean running) {
        run = running;
    }

    /**
     * Sets the game mode.
     */
    void setState(int mode) {
        synchronized (surfaceHolder) {
            state = mode;
            Resources res = context.getResources();
            switch (state) {
                case STATE_READY:
                    newRound();
                    break;
                case STATE_RUNNING:
                    hideStatusText();
                    break;
                case STATE_WIN:
                    setStatusText(res.getString(R.string.mode_win));
                    computerPlayer.score = computerPlayer.score - 2;
                    newRound();
                    break;
                case STATE_LOSE:
                    setStatusText(res.getString(R.string.mode_lose));
                    humanPlayer.score = humanPlayer.score - 2;
                    newRound();
                    break;
                case STATE_PAUSE:
                    setStatusText(res.getString(R.string.mode_pause));
                    break;
            }
        }
    }

    /**
     * Pauses the animation.
     */
    void pause() {
        synchronized (surfaceHolder) {
            if (state == STATE_RUNNING) {
                setState(STATE_PAUSE);
            }
        }
    }

    /**
     * Resumes from a pause.
     */
    void unPause() {
        synchronized (surfaceHolder) {
            setState(STATE_RUNNING);
        }
    }

    /**
     * Reset score and start new game.
     */
    void newGame() {
        synchronized (surfaceHolder) {
            humanPlayer.score = 0;
            computerPlayer.score = 0;
            newRound();
            setState(STATE_RUNNING);
        }
    }

    /**
     * return true if the game is in win, lose or pause state.
     */
    boolean betweenRounds() {
        return state != STATE_RUNNING;
    }

    /**
     * Returns true if the touch event is on Player A paddle bounds.
     *
     * param event touch even
     * return true if touch is on Player A paddle
     */
    boolean touchOnHumanPaddle(MotionEvent event) {
        return humanPlayer.bounds.contains(event.getX(), event.getY());
    }

    /**
     * Move Player A paddle according to touch event.
     *
     * @param event touch move event
     */
    void humanPaddleHandler(MotionEvent event) {
        synchronized (surfaceHolder) {
            movePlayer(humanPlayer,
                    humanPlayer.bounds.left,
                    event.getY() - humanPlayer.paddleHeight / 2);
        }
    }

    /**
     * Callback invoked when the surface dimensions change.
     *
     * @param width  canvas width
     * @param height canvas height
     */
    void setSurfaceSize(int width, int height) {
        synchronized (surfaceHolder) {
            canvasWidth = width;
            canvasHeight = height;
            newRound();
        }
    }

    /**
     * Update paddle and player positions, check for collisions, win or lose.
     */
    private void updatePhysics() {

        if (humanPlayer.collision > 0) {
            humanPlayer.collision--;
            possesion = true;
        }
        if(computerPlayer.collision > 0) {
            computerPlayer.collision--;
            possesion = false;
        }

        if (collision(humanPlayer, ball)) {
            handleCollision(humanPlayer, ball);
            humanPlayer.collision = COLLISION_FRAMES;
        } else if (collision(computerPlayer, ball)) {
            handleCollision(computerPlayer, ball);
            computerPlayer.collision = COLLISION_FRAMES;
        } else if (topBottomCollision()) {
            ball.dy = -ball.dy;
        } else if (rightWallCollision()) {
            setState(STATE_WIN);
            return;
        } else if (leftWallCollision()) {
            setState(STATE_LOSE);
            return;
        } else if (gateCollision(pointGate, ball)){
            gateCollisionHandler(pointGate, ball);
        }

        if (randomGen.nextFloat() < computerMoveProbability) {
            aiMovements();
        }

        moveBall();
    }

    private void moveBall() {
        ball.cx += ball.dx;
        ball.cy += ball.dy;

        if (ball.cy < ball.radius) {
            ball.cy = ball.radius;
        } else if (ball.cy + ball.radius >= canvasHeight) {
            ball.cy = canvasHeight - ball.radius - 1;
        }
    }

    /**
     * Move the computer paddle to hit the ball.
     */
    private void aiMovements() {
        if (computerPlayer.bounds.top > ball.cy) {
            // move up
            movePlayer(computerPlayer,
                    computerPlayer.bounds.left,
                    computerPlayer.bounds.top - PADDLE_SPEED);
        } else if (computerPlayer.bounds.top + computerPlayer.paddleHeight < ball.cy) {
            // move down
            movePlayer(computerPlayer,
                    computerPlayer.bounds.left,
                    computerPlayer.bounds.top + PADDLE_SPEED);
        }
    }

    private boolean leftWallCollision() {
        return ball.cx <= ball.radius;
    }

    private boolean rightWallCollision() {
        return ball.cx + ball.radius >= canvasWidth - 1;
    }

    private boolean topBottomCollision() {
        return ball.cy <= ball.radius
                || ball.cy + ball.radius >= canvasHeight - 1;
    }

    /**
     * Draws the score, paddles and the ball.
     */
    private void updateDisplay(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        canvas.drawRect(0, 0, canvasWidth, canvasHeight, canvasBoundsPaint);

        final int middle = canvasWidth / 2;
        canvas.drawLine(middle, 1, middle, canvasHeight - 1, medianLinePaint);

        setScoreText(humanPlayer.score + "    " + computerPlayer.score);

        handleHit(humanPlayer);
        handleHit(computerPlayer);

        canvas.drawRoundRect(humanPlayer.bounds, 5, 5, humanPlayer.paint);
        canvas.drawRoundRect(computerPlayer.bounds, 5, 5, computerPlayer.paint);
        canvas.drawCircle(ball.cx, ball.cy, ball.radius, ball.paint);
        gateCreate(canvas);

    }

    private void gateCreate(Canvas canvas) {
        Random rand = new Random();
        int randNum = rand.nextInt(10);
        for (int i = randNum; i > 0; i--) {
            canvas.drawRoundRect(pointGate.bounds, 5,
                    5, pointGate.paint);
            moveGate(pointGate);
        }
    }

    private void handleHit(Player player) {
        if (player.collision > 0) {
            player.paint.setShadowLayer(10, 0, 0, Color.YELLOW);
        } else {
            player.paint.setShadowLayer(0, 0, 0, 0);
        }
    }

    /**
     * Reset players and ball position for a new round.
     */
    private void newRound() {
        ball.cx = canvasWidth / 2;
        ball.cy = canvasHeight / 2;
        ball.dx = -BALL_SPEED;
        ball.dy = 0;

        //moveGate(pointGate);

        movePlayer(humanPlayer,
                2,
                (canvasHeight - humanPlayer.paddleHeight) / 2);

        movePlayer(computerPlayer,
                canvasWidth - computerPlayer.paddleWidth - 2,
                (canvasHeight - computerPlayer.paddleHeight) / 2);
    }

    private void setStatusText(String text) {
        Message msg = statusHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("text", text);
        b.putInt("vis", View.VISIBLE);
        msg.setData(b);
        statusHandler.sendMessage(msg);
    }

    private void hideStatusText() {
        Message msg = statusHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt("vis", View.INVISIBLE);
        msg.setData(b);
        statusHandler.sendMessage(msg);
    }

    private void setScoreText(String text) {
        Message msg = scoreHandler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("text", text);
        msg.setData(b);
        scoreHandler.sendMessage(msg);
    }

    private void moveGate(PointGate gate){
        Random rand = new Random();
        final int left = rand.nextInt(canvasWidth);
        final int top = rand.nextInt(canvasHeight);
        gate.bounds.offsetTo(left, top);
    }

    private void movePlayer(Player player, float left, float top) {
        if (left < 2) {
            left = 2;
        } else if (left + player.paddleWidth >= canvasWidth - 2) {
            left = canvasWidth - player.paddleWidth - 2;
        }
        if (top < 0) {
            top = 0;
        } else if (top + player.paddleHeight >= canvasHeight) {
            top = canvasHeight - player.paddleHeight - 1;
        }
        player.bounds.offsetTo(left, top);
    }

    private boolean collision(Player player, Ball ball) {
        return player.bounds.intersects(
                ball.cx - this.ball.radius,
                ball.cy - this.ball.radius,
                ball.cx + this.ball.radius,
                ball.cy + this.ball.radius);
    }

    private boolean gateCollision(PointGate gate, Ball ball){
        return gate.bounds.intersects(ball.cx - this.ball.radius,
                ball.cy - this.ball.radius,
                ball.cx + this.ball.radius,
                ball.cy + this.ball.radius);
    }

    /**
     * Compute ball direction after collision with player paddle.
     */
    private void handleCollision(Player player, Ball ball) {
        float relativeIntersectY = player.bounds.top + player.paddleHeight / 2 - ball.cy;
        float normalizedRelativeIntersectY = relativeIntersectY / (player.paddleHeight / 2);
        double bounceAngle = normalizedRelativeIntersectY * MAX_BOUNCE_ANGLE;


        ball.dx = (float) (-Math.signum(ball.dx) * BALL_SPEED * Math.cos(bounceAngle));
        ball.dy = (float) (BALL_SPEED * -Math.sin(bounceAngle));

        if (player == humanPlayer) {
            this.ball.cx = humanPlayer.bounds.right + this.ball.radius;
        } else {
            this.ball.cx = computerPlayer.bounds.left - this.ball.radius;
        }
    }

    private void gateCollisionHandler(PointGate gate, Ball ball){

        if (possesion == true) {
            humanPlayer.score++;
        } else {
            computerPlayer.score++;
        }
        gate = null;
    }


}
