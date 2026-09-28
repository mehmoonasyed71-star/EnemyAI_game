import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class DarkKnight extends JFrame {
    public static final int TILE = 40;
    public static final int COLS = 20;
    public static final int ROWS = 15;
    public static final int WIDTH = TILE * COLS;  // 800
    public static final int HEIGHT = TILE * ROWS; // 600

    public DarkKnight() {
        setTitle("DARK KNIGHT: SHADOW HUNT");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(new GamePanel());
        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DarkKnight().setVisible(true));
    }
}

class GamePanel extends JPanel implements Runnable, KeyListener {
    private Thread thread;
    private boolean running;

    private int state = 0; // 0=Menu 1=Play 2=Over 3=Win
    private Player player;
    private List<Enemy> enemies;
    private List<Projectile> projectiles;
    private List<Particle> particles;
    private int[][] map;
    private int[][] stars;

    private boolean up, down, left, right, fire, dash, special;
    private int fireCooldown = 0;
    private long startTime;

    public GamePanel() {
        setPreferredSize(new Dimension(DarkKnight.WIDTH, DarkKnight.HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        stars = new int[80][3];
        Random rnd = new Random();
        for (int i = 0; i < 80; i++) {
            stars[i][0] = rnd.nextInt(DarkKnight.WIDTH);
            stars[i][1] = rnd.nextInt(DarkKnight.HEIGHT);
            stars[i][2] = rnd.nextInt(3) + 1;
        }
    }

    public void addNotify() {
        super.addNotify();
        if (thread == null) {
            thread = new Thread(this);
            running = true;
            thread.start();
        }
    }

    private void initGame() {
        player = new Player(DarkKnight.TILE * 1.5, DarkKnight.TILE * 1.5);
        enemies = new ArrayList<>();
        projectiles = new ArrayList<>();
        particles = new ArrayList<>();
        startTime = System.currentTimeMillis();

        map = new int[DarkKnight.ROWS][DarkKnight.COLS];
        for (int r = 0; r < DarkKnight.ROWS; r++)
            for (int c = 0; c < DarkKnight.COLS; c++) {
                if (r == 0 || c == 0 || r == DarkKnight.ROWS-1 || c == DarkKnight.COLS-1) map[r][c] = 1;
                else if (r % 2 == 0 && c % 2 == 0) map[r][c] = 1;
                else map[r][c] = 0;
            }
        map[1][1] = 0; map[1][2] = 0; map[2][1] = 0;

        int T = DarkKnight.TILE;
        enemies.add(new Enemy(18*T + T/2.0, 1*T  + T/2.0, 0));
        enemies.add(new Enemy(1*T  + T/2.0, 13*T + T/2.0, 1));
    }

    public void run() {
        long last = System.nanoTime();
        double ns = 1_000_000_000 / 60.0, delta = 0;
        while (running) {
            long now = System.nanoTime();
            delta += (now - last) / ns; last = now;
            while (delta >= 1) { update(); delta--; }
            repaint();
            try { Thread.sleep(2); } catch (Exception e) {}
        }
    }

    private void update() {
        if (state != 1) return;
        if (fireCooldown > 0) fireCooldown--;

        player.update(up, down, left, right, dash, map);

        if (fire && fireCooldown <= 0) {
            projectiles.add(new Projectile(player.x, player.y, player.angle, true));
            fireCooldown = 12;
        }

        if (special && player.energy >= 50) {
            player.energy -= 50;
            spawnParticles(player.x, player.y, 50, new Color(0, 220, 255));
            for (Enemy e : enemies)
                if (Math.hypot(e.x - player.x, e.y - player.y) < 200) e.hp -= 20;
            special = false;
        }

        Iterator<Projectile> pIt = projectiles.iterator();
        while (pIt.hasNext()) {
            Projectile p = pIt.next();
            p.update();
            if (p.checkCollision(map)) {
                spawnParticles(p.x, p.y, 6, p.isPlayer ? new Color(0,220,255) : new Color(255,60,60));
                pIt.remove(); continue;
            }
            boolean hit = false;
            if (p.isPlayer) {
                for (Enemy e : enemies)
                    if (Math.hypot(p.x-e.x, p.y-e.y) < 22) { e.hp -= 3; hit = true; spawnParticles(e.x,e.y,10,e.getColor()); break; }
            } else {
                if (Math.hypot(p.x-player.x, p.y-player.y) < 16) { player.hp -= 8; hit = true; spawnParticles(player.x,player.y,10,Color.RED); }
            }
            if (hit) pIt.remove();
        }

        Iterator<Enemy> eIt = enemies.iterator();
        while (eIt.hasNext()) {
            Enemy e = eIt.next();
            e.update(player, map, projectiles);
            if (Math.hypot(e.x-player.x, e.y-player.y) < 26) player.hp -= 1;
            if (e.hp <= 0) { spawnParticles(e.x,e.y,35,e.getColor()); eIt.remove(); }
        }

        particles.removeIf(pt -> { pt.update(); return pt.life <= 0; });

        if (player.hp <= 0) state = 2;
        if (enemies.isEmpty()) state = 3;
    }

    private void spawnParticles(double x, double y, int n, Color c) {
        for (int i = 0; i < n; i++) particles.add(new Particle(x, y, c));
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2);

        if (state == 0) {
            drawMenu(g2);
        } else {
            drawMaze(g2);
            for (Particle pt : particles) pt.draw(g2);
            for (Projectile p : projectiles) p.draw(g2);
            for (Enemy e : enemies) e.draw(g2);
            player.draw(g2);
            drawHUD(g2);
            if (state == 2) drawOverlay(g2, "GAME OVER",       "Press ENTER to Restart",   new Color(220,30,30),  new Color(255,80,80));
            if (state == 3) drawOverlay(g2, "MISSION CLEARED", "Press ENTER to Play Again", new Color(0,180,80),   new Color(0,255,120));
        }
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0,0, new Color(2,2,12), DarkKnight.WIDTH, DarkKnight.HEIGHT, new Color(0,10,35)));
        g.fillRect(0, 0, DarkKnight.WIDTH, DarkKnight.HEIGHT);
        long t = System.currentTimeMillis();
        for (int[] s : stars) {
            float a = 0.3f + 0.5f*(float)Math.abs(Math.sin(t/800.0 + s[0]));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setColor(Color.WHITE);
            g.fillOval(s[0], s[1], s[2], s[2]);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private void drawMaze(Graphics2D g) {
        for (int r = 0; r < DarkKnight.ROWS; r++)
            for (int c = 0; c < DarkKnight.COLS; c++) {
                int x = c*DarkKnight.TILE, y = r*DarkKnight.TILE;
                if (map[r][c] == 1) {
                    g.setColor(new Color(0,40,120,100));
                    g.fillRect(x-2, y-2, DarkKnight.TILE+4, DarkKnight.TILE+4);
                    g.setPaint(new GradientPaint(x,y, new Color(0,80,200), x+DarkKnight.TILE,y+DarkKnight.TILE, new Color(0,30,100)));
                    g.fillRect(x, y, DarkKnight.TILE, DarkKnight.TILE);
                    g.setColor(new Color(0,200,255,180));
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRect(x, y, DarkKnight.TILE, DarkKnight.TILE);
                    g.setColor(new Color(0,180,255,40));
                    g.fillRect(x+3, y+3, DarkKnight.TILE-6, DarkKnight.TILE-6);
                } else {
                    g.setColor(new Color(6,8,18));
                    g.fillRect(x, y, DarkKnight.TILE, DarkKnight.TILE);
                    g.setColor(new Color(0,60,120,25));
                    g.drawRect(x, y, DarkKnight.TILE, DarkKnight.TILE);
                }
            }
    }

    private void drawHUD(Graphics2D g) {
        int W = DarkKnight.WIDTH;
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        // Left panel
        g.setColor(new Color(0,0,0,200));
        g.fillRoundRect(8, 8, 280, 72, 14, 14);
        g.setColor(new Color(0,180,255,120));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(8, 8, 280, 72, 14, 14);

        g.setFont(new Font("Impact", Font.PLAIN, 12));
        g.setColor(new Color(255,80,80));
        g.drawString("HP", 18, 28);
        g.setColor(new Color(60,10,10));
        g.fillRoundRect(40, 16, 238, 13, 5, 5);
        int hpW = (int)(238 * Math.max(0, player.hp/100.0));
        g.setPaint(new GradientPaint(40,16, new Color(220,30,30), 40+hpW,16, new Color(255,100,50)));
        g.fillRoundRect(40, 16, hpW, 13, 5, 5);

        g.setFont(new Font("Impact", Font.PLAIN, 12));
        g.setColor(new Color(0,220,255));
        g.drawString("EN", 18, 48);
        g.setColor(new Color(0,15,40));
        g.fillRoundRect(40, 36, 238, 13, 5, 5);
        int enW = (int)(238 * Math.max(0, player.energy/100.0));
        g.setPaint(new GradientPaint(40,36, new Color(0,120,220), 40+enW,36, new Color(0,220,255)));
        g.fillRoundRect(40, 36, enW, 13, 5, 5);

        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(new Color(100,160,220));
        g.drawString("WASD=Move  F=Fire  Space=Burst  Shift=Dash", 14, 68);

        // Enemy counter (top right)
        g.setColor(new Color(0,0,0,180));
        g.fillRoundRect(W-160, 8, 152, 46, 12, 12);
        g.setColor(new Color(255,140,0,120));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(W-160, 8, 152, 46, 12, 12);
        g.setFont(new Font("Impact", Font.PLAIN, 12));
        g.setColor(new Color(255,180,60));
        g.drawString("ENEMIES", W-150, 25);
        g.setFont(new Font("Impact", Font.BOLD, 20));
        g.setColor(enemies.size() > 0 ? new Color(255,80,60) : new Color(60,255,120));
        g.drawString(enemies.size() + " / 4", W-150, 46);

        // Timer (top center)
        g.setColor(new Color(0,0,0,160));
        g.fillRoundRect(W/2-45, 8, 90, 30, 10, 10);
        g.setFont(new Font("Impact", Font.BOLD, 18));
        g.setColor(new Color(160,220,255));
        String ts = String.format("%02d:%02d", elapsed/60, elapsed%60);
        g.drawString(ts, W/2 - g.getFontMetrics().stringWidth(ts)/2, 28);
    }

    private void drawMenu(Graphics2D g) {
        int W = DarkKnight.WIDTH, H = DarkKnight.HEIGHT;
        long t = System.currentTimeMillis();

        // Scanlines
        for (int y = 0; y < H; y += 4) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.04f + 0.02f*(float)Math.abs(Math.sin(y/30.0+t/300.0))));
            g.setColor(Color.CYAN);
            g.fillRect(0, y, W, 2);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // Title shadow
        g.setFont(new Font("Impact", Font.BOLD, 72));
        String title = "DARK KNIGHT";
        int tx = W/2 - g.getFontMetrics().stringWidth(title)/2;
        for (int i = 0; i < 8; i++) { g.setColor(new Color(0,100,200,70)); g.drawString(title, tx-i, H/2-60+i); }
        g.setPaint(new GradientPaint(0, H/2-110, new Color(0,200,255), 0, H/2-50, new Color(0,80,200)));
        g.drawString(title, tx, H/2-60);

        // Subtitle
        g.setFont(new Font("Impact", Font.PLAIN, 26));
        g.setColor(new Color(255,180,0));
        String sub = "SHADOW HUNT";
        g.drawString(sub, W/2 - g.getFontMetrics().stringWidth(sub)/2, H/2-10);

        // Divider
        g.setColor(new Color(0,180,255,120));
        g.setStroke(new BasicStroke(2));
        g.drawLine(W/2-160, H/2+8, W/2+160, H/2+8);

        // Press Enter (pulsing)
        float pulse = 0.5f + 0.5f*(float)Math.abs(Math.sin(t/500.0));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        String enter = "Press ENTER to Start";
        g.drawString(enter, W/2 - g.getFontMetrics().stringWidth(enter)/2, H/2+40);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // Controls
        g.setFont(new Font("Arial", Font.PLAIN, 13));
        g.setColor(new Color(100,160,220));
        String[] ctrl = {"WASD / Arrow Keys  =  Move", "F = Fire   |   Space = Energy Burst   |   Shift = Dash"};
        for (int i = 0; i < ctrl.length; i++)
            g.drawString(ctrl[i], W/2 - g.getFontMetrics().stringWidth(ctrl[i])/2, H/2+80+i*22);

        // Footer
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.setColor(new Color(60,100,160));
        g.drawString("(C) 2025 DARK KNIGHT STUDIOS", W/2-88, H-12);
    }

    private void drawOverlay(Graphics2D g, String title, String sub, Color dark, Color bright) {
        int W = DarkKnight.WIDTH, H = DarkKnight.HEIGHT;
        g.setColor(new Color(0,0,0,210));
        g.fillRect(0, 0, W, H);

        g.setColor(new Color(dark.getRed(),dark.getGreen(),dark.getBlue(),60));
        g.fillRoundRect(W/2-250, H/2-90, 500, 160, 20, 20);
        g.setColor(new Color(bright.getRed(),bright.getGreen(),bright.getBlue(),150));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(W/2-250, H/2-90, 500, 160, 20, 20);

        g.setFont(new Font("Impact", Font.BOLD, 58));
        int tx = W/2 - g.getFontMetrics().stringWidth(title)/2;
        g.setColor(dark);   g.drawString(title, tx+4, H/2-20+4);
        g.setColor(bright); g.drawString(title, tx,   H/2-20);

        float pulse = 0.5f+0.5f*(float)Math.abs(Math.sin(System.currentTimeMillis()/500.0));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
        g.setFont(new Font("Arial", Font.BOLD, 18));
        g.setColor(Color.WHITE);
        g.drawString(sub, W/2 - g.getFontMetrics().stringWidth(sub)/2, H/2+38);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (state == 0 || state == 2 || state == 3) {
            if (k == KeyEvent.VK_ENTER) { initGame(); state = 1; }
        } else {
            if (k == KeyEvent.VK_UP    || k == KeyEvent.VK_W) up    = true;
            if (k == KeyEvent.VK_DOWN  || k == KeyEvent.VK_S) down  = true;
            if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) left  = true;
            if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = true;
            if (k == KeyEvent.VK_SHIFT) dash    = true;
            if (k == KeyEvent.VK_F)     fire    = true;
            if (k == KeyEvent.VK_SPACE) special = true;
        }
    }
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_UP    || k == KeyEvent.VK_W) up    = false;
        if (k == KeyEvent.VK_DOWN  || k == KeyEvent.VK_S) down  = false;
        if (k == KeyEvent.VK_LEFT  || k == KeyEvent.VK_A) left  = false;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) right = false;
        if (k == KeyEvent.VK_SHIFT) dash    = false;
        if (k == KeyEvent.VK_F)     fire    = false;
        if (k == KeyEvent.VK_SPACE) special = false;
    }
    public void keyTyped(KeyEvent e) {}
}

class Player {
    double x, y, angle;
    int hp = 100, energy = 100, dashTimer = 0;

    public Player(double x, double y) { this.x = x; this.y = y; }

    public void update(boolean u, boolean d, boolean l, boolean r, boolean dashKey, int[][] map) {
        double dx = (l?-1:0)+(r?1:0), dy = (u?-1:0)+(d?1:0);
        if (dx != 0 || dy != 0) angle = Math.atan2(dy, dx);

        double speed = 4;
        if (dashTimer > 0) { speed = 13; dashTimer--; }
        else if (dashKey && energy >= 30) { energy -= 30; dashTimer = 10; }
        else if (energy < 100 && Math.random() < 0.08) energy++;

        double mx = Math.cos(angle)*speed, my = Math.sin(angle)*speed;
        if (dx == 0 && dy == 0 && dashTimer == 0) { mx = 0; my = 0; }

        if (!wall(x+mx, y,    map)) x += mx;
        if (!wall(x,    y+my, map)) y += my;
    }

    private boolean wall(double nx, double ny, int[][] map) {
        int r = (int)(ny/DarkKnight.TILE), c = (int)(nx/DarkKnight.TILE);
        if (r<0||c<0||r>=DarkKnight.ROWS||c>=DarkKnight.COLS) return true;
        return map[r][c] == 1;
    }

    public void draw(Graphics2D g) {
        AffineTransform old = g.getTransform();
        g.translate(x, y); g.rotate(angle);
        long t = System.currentTimeMillis();

        // Pulsing aura
        float pa = 0.3f+0.2f*(float)Math.abs(Math.sin(t/300.0));
        g.setPaint(new RadialGradientPaint(0,0,45, new float[]{0f,1f},
            new Color[]{new Color(0,160,255,(int)(255*pa)), new Color(0,0,0,0)}));
        g.fillOval(-45,-45,90,90);

        // Cape
        g.setColor(new Color(180,0,0));
        int w1=(int)(Math.sin(t/90.0)*5), w2=(int)(Math.sin(t/110.0+1)*4);
        g.fillPolygon(new int[]{-12,12,18,0,-18}, new int[]{10,10,30+w1,22+w2,30-w1}, 5);
        g.setColor(new Color(220,40,40,120));
        g.fillPolygon(new int[]{-5,5,10}, new int[]{10,10,25+w1}, 3);

        // Body
        g.setPaint(new GradientPaint(-12,-12, new Color(20,20,60), 12,12, new Color(0,0,30)));
        g.fillRoundRect(-13,-13,26,26,10,10);

        // Bat symbol
        g.setColor(new Color(255,220,0));
        g.fillOval(-9,-3,8,5); g.fillOval(1,-3,8,5); g.fillOval(-4,-5,8,6);

        // Cowl
        g.setColor(new Color(10,10,30));
        g.fillOval(-14,-14,28,20);
        g.setColor(new Color(20,20,60));
        g.fillPolygon(new int[]{-8,-4,-12}, new int[]{-14,-24,-24}, 3);
        g.fillPolygon(new int[]{8,4,12},    new int[]{-14,-24,-24}, 3);

        // Eyes
        g.setColor(new Color(180,220,255));
        g.fillRect(-8,-8,5,3); g.fillRect(3,-8,5,3);
        g.setColor(new Color(100,200,255,180));
        g.fillRect(-9,-9,7,5); g.fillRect(2,-9,7,5);

        g.setTransform(old);
    }
}

class Enemy {
    double x, y, angle;
    int type, hp, maxHp, pathTimer=0, shootCooldown=0;
    Point nextWaypoint = null;

    public Enemy(double x, double y, int type) {
        this.x=x; this.y=y; this.type=type;
        maxHp=25+type*10; hp=maxHp;
    }

    public Color getColor() {
        if (type==0) return new Color(0,220,80);
        if (type==1) return new Color(255,50,50);
        if (type==2) return new Color(200,0,220);
        return new Color(255,150,0);
    }

    public String getName() {
        return new String[]{"VENOM","INFERNO","PHANTOM","CHAOS"}[type];
    }

    public void update(Player p, int[][] map, List<Projectile> proj) {
        if (--pathTimer <= 0) {
            nextWaypoint = bfs(map,(int)(y/DarkKnight.TILE),(int)(x/DarkKnight.TILE),
                                   (int)(p.y/DarkKnight.TILE),(int)(p.x/DarkKnight.TILE));
            pathTimer=15;
        }
        double speed=0.9+type*0.12;
        if (nextWaypoint!=null) {
            double tx=nextWaypoint.x*DarkKnight.TILE+DarkKnight.TILE/2.0, ty=nextWaypoint.y*DarkKnight.TILE+DarkKnight.TILE/2.0;
            angle=Math.atan2(ty-y,tx-x);
            if (Math.hypot(tx-x,ty-y)>speed) { x+=Math.cos(angle)*speed; y+=Math.sin(angle)*speed; }
        } else { angle=Math.atan2(p.y-y,p.x-x); x+=Math.cos(angle)*speed; y+=Math.sin(angle)*speed; }

        if (--shootCooldown<=0 && Math.hypot(p.x-x,p.y-y)<420) {
            proj.add(new Projectile(x,y,angle,false));
            shootCooldown=18+(int)(Math.random()*14);
        }
    }

    private Point bfs(int[][] map, int sr, int sc, int tr, int tc) {
        if (sr==tr&&sc==tc) return null;
        int[] dr={-1,1,0,0}, dc={0,0,-1,1};
        boolean[][] vis=new boolean[DarkKnight.ROWS][DarkKnight.COLS];
        Queue<int[]> q=new LinkedList<>();
        int[][][] prev=new int[DarkKnight.ROWS][DarkKnight.COLS][2];
        for (int[][] row:prev) for (int[] cell:row) { cell[0]=-1; cell[1]=-1; }
        q.add(new int[]{sr,sc}); vis[sr][sc]=true;
        while (!q.isEmpty()) {
            int[] cur=q.poll();
            if (cur[0]==tr&&cur[1]==tc) {
                int r=tr,c=tc;
                while (prev[r][c][0]!=sr||prev[r][c][1]!=sc) { int pr=prev[r][c][0],pc=prev[r][c][1]; r=pr; c=pc; }
                return new Point(c,r);
            }
            for (int i=0;i<4;i++) {
                int nr=cur[0]+dr[i],nc=cur[1]+dc[i];
                if (nr>=0&&nr<DarkKnight.ROWS&&nc>=0&&nc<DarkKnight.COLS&&!vis[nr][nc]&&map[nr][nc]==0) {
                    vis[nr][nc]=true; prev[nr][nc]=new int[]{cur[0],cur[1]}; q.add(new int[]{nr,nc});
                }
            }
        }
        return null;
    }

    public void draw(Graphics2D g) {
        AffineTransform old=g.getTransform();
        g.translate(x,y);
        long t=System.currentTimeMillis();
        g.translate(0,Math.sin(t/150.0+type)*3);
        g.rotate(angle);

        Color c=getColor();
        float pa=0.25f+0.2f*(float)Math.abs(Math.sin(t/400.0+type));
        g.setPaint(new RadialGradientPaint(0,0,38, new float[]{0f,1f},
            new Color[]{new Color(c.getRed(),c.getGreen(),c.getBlue(),(int)(255*pa)), new Color(0,0,0,0)}));
        g.fillOval(-38,-38,76,76);

        g.setColor(c.darker().darker()); g.fillOval(-17,-17,34,34);
        g.setColor(c.darker());          g.fillOval(-13,-13,26,26);
        g.setColor(c);

        if (type==0) {
            g.fillPolygon(new int[]{-8,-4,-14}, new int[]{-13,-2,-28},3);
            g.fillPolygon(new int[]{8,4,14},    new int[]{-13,-2,-28},3);
            g.setStroke(new BasicStroke(2)); g.drawOval(-20,5,40,15);
        } else if (type==1) {
            for (int i=-2;i<=2;i++) g.fillPolygon(new int[]{i*6-3,i*6,i*6+3}, new int[]{-13,-28,-13},3);
            g.setColor(new Color(255,220,0,180));
            g.fillPolygon(new int[]{-6,0,6}, new int[]{-12,-22,-12},3);
        } else if (type==2) {
            g.fillPolygon(new int[]{-13,-28,-20,-13}, new int[]{-5,-20,5,5},4);
            g.fillPolygon(new int[]{13,28,20,13},     new int[]{-5,-20,5,5},4);
        } else {
            g.setColor(new Color(255,230,0));
            for (int i=0;i<6;i++) { double a=i*Math.PI/3; g.fillOval((int)(Math.cos(a)*14)-3,(int)(Math.sin(a)*14)-3,6,6); }
        }

        g.setColor(new Color(255,40,40));   g.fillOval(-7,-7,5,5); g.fillOval(2,-7,5,5);
        g.setColor(new Color(255,150,150,180)); g.fillOval(-6,-6,3,3); g.fillOval(3,-6,3,3);

        g.rotate(-angle);
        g.setColor(new Color(60,0,0)); g.fillRoundRect(-18,-28,36,5,3,3);
        g.setPaint(new GradientPaint(-18,-28,new Color(200,0,0),-18+(int)(36*((double)hp/maxHp)),-28,new Color(80,220,0)));
        g.fillRoundRect(-18,-28,(int)(36*((double)hp/maxHp)),5,3,3);
        g.setFont(new Font("Impact",Font.PLAIN,9)); g.setColor(c);
        g.drawString(getName(),-14,-30);

        g.setTransform(old);
    }
}

class Projectile {
    double x, y, dx, dy; boolean isPlayer;

    public Projectile(double x, double y, double angle, boolean isPlayer) {
        this.x=x; this.y=y; this.isPlayer=isPlayer;
        double s=isPlayer?15:7; dx=Math.cos(angle)*s; dy=Math.sin(angle)*s;
    }
    public void update() { x+=dx; y+=dy; }
    public boolean checkCollision(int[][] map) {
        int r=(int)(y/DarkKnight.TILE), c=(int)(x/DarkKnight.TILE);
        if (r<0||c<0||r>=DarkKnight.ROWS||c>=DarkKnight.COLS) return true;
        return map[r][c]==1;
    }
    public void draw(Graphics2D g) {
        long t=System.currentTimeMillis();
        if (isPlayer) {
            AffineTransform old=g.getTransform();
            g.translate(x,y); g.rotate(t/80.0);
            g.setColor(new Color(255,220,0));
            g.fillPolygon(new int[]{0,3,8,3,0,-3,-8,-3}, new int[]{-8,-3,0,3,8,3,0,-3},8);
            g.setColor(new Color(0,200,255,180)); g.fillOval(-3,-3,6,6);
            g.setTransform(old);
        } else {
            g.setColor(new Color(255,60,60,200));  g.fillOval((int)x-7,(int)y-7,14,14);
            g.setColor(new Color(255,180,0));       g.fillOval((int)x-3,(int)y-3,6,6);
        }
    }
}

class Particle {
    double x, y, dx, dy; float life=1f, size; Color color;

    public Particle(double x, double y, Color c) {
        this.x=x; this.y=y; this.color=c;
        double a=Math.random()*Math.PI*2, s=Math.random()*5+1;
        dx=Math.cos(a)*s; dy=Math.sin(a)*s; size=(float)(Math.random()*5+2);
    }
    public void update() { x+=dx; y+=dy; dx*=0.96; dy*=0.96; life-=0.035f; }
    public void draw(Graphics2D g) {
        if (life<=0) return;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0,life)));
        g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),60));
        g.fillOval((int)(x-size*2),(int)(y-size*2),(int)(size*4),(int)(size*4));
        g.setColor(color);
        g.fillOval((int)(x-size/2),(int)(y-size/2),(int)size,(int)size);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1f));
    }
}

