package me.ayunami2000.potplayerlastfm;

import com.del.potplayercontrol.api.JNAPotPlayerHelper;
import com.del.potplayercontrol.api.PlayStatus;
import com.del.potplayercontrol.api.PotPlayer;
import com.del.potplayercontrol.impl.JNAPotPlayer;
import com.del.potplayercontrol.impl.Window;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.win32.User32;
import de.umass.lastfm.Authenticator;
import de.umass.lastfm.Caller;
import de.umass.lastfm.Session;
import de.umass.lastfm.Track;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

import javax.swing.*;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 4) {
            if (System.console() == null) {
                args = new String[4];
                args[0] = JOptionPane.showInputDialog("Please enter username");
                if (args[0] == null) return;
                args[1] = JOptionPane.showInputDialog("Please enter password (or md5 of password)");
                if (args[1] == null) return;
                args[2] = JOptionPane.showInputDialog("Please enter api key");
                if (args[2] == null) return;
                args[3] = JOptionPane.showInputDialog("Please enter secret");
                if (args[3] == null) return;
            } else {
                System.err.println("Required arguments: username, password (or md5 of password), api key, secret\nRun with javaw (or double click the jar file) for GUI-based entry");
                return;
            }
        }
        if (SystemTray.isSupported()) {
            final PopupMenu popup = new PopupMenu();
            final TrayIcon trayIcon = new TrayIcon(ImageIO.read(Objects.requireNonNull(Main.class.getResource("/icon.png"))));
            final SystemTray tray = SystemTray.getSystemTray();

            MenuItem exitItem = new MenuItem("Exit PotPlayer LastFM");
            exitItem.addActionListener(e -> System.exit(0));
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("TrayIcon could not be added.");
            }
        } else {
            System.err.println("SystemTray is not supported");
        }

        String name = "PotPlayer";
        String end = " - " + name;
        Caller.getInstance().setUserAgent("PotPlayer-LastFM");

        PotPlayer player;
        Session session = Authenticator.getMobileSession(args[0], args[1], args[2], args[3]);
        if (session == null) {
            System.err.println("Error authenticating with LastFM.");
            System.exit(-1);
            return;
        }
        while (true) {
            List<Window> windows = JNAPotPlayerHelper.getAllPlayerWindows(window -> window.getWindowText().endsWith(end) || window.getWindowText().equals(name));
            if (!windows.isEmpty()) {
                player = new JNAPotPlayer(windows.get(0));
                if (player.getPlayStatus() == PlayStatus.Undefined) {
                    Thread.sleep(160);
                    continue;
                }
                String t;
                PlayStatus ps;
                long ct;
                Instant startTime = Instant.now();
                Instant startTime2 = Instant.now();
                boolean scrobbled = false;
                String currSong = null;
                while (User32.INSTANCE.IsWindow(player.getWindow().getHwnd())) {
                    String tmp = WindowUtils.getWindowTitle(player.getWindow().getHwnd());
                    if (!tmp.endsWith(end) && !tmp.equals(name)) break;
                    PlayStatus tmpps = player.getPlayStatus();
					ct = player.getCurrentTime();
                    t = tmp;
                    ps = tmpps;
                    String song = t;
                    if (song.endsWith(end)) song = song.substring(0, song.lastIndexOf(end));
                    if (song.matches("\\.[a-zA-Z0-9]+$")) {
                        song = song.substring(0, song.lastIndexOf('.'));
                        song = song.replace('_', ' ');
                    } else {
                        song = song.replaceFirst(" \\([0-9-]+\\)$", "");
                    }
                    if (!song.equals(currSong)) {
                        scrobbled = false;
                        startTime = Instant.now();
                        startTime2 = Instant.now();
                        currSong = song;
                        if (ps == PlayStatus.Running || ps == PlayStatus.Paused) {
                            String artistName = currSong.substring(0, currSong.indexOf(" - "));
                            String trackName = currSong.substring(artistName.length() + 3);
                            Track.updateNowPlaying(artistName, trackName, session);
                        }
                    }
                    if (ps == PlayStatus.Running) {
                        if (ct < 1000) {
                            startTime = Instant.now();
                            scrobbled = false;
                            currSong = null;
                        }
                        if (!scrobbled && Duration.between(startTime, Instant.now()).getSeconds() > Math.min(3 * 60, player.getTotalTime() / 2000)) {
                            scrobbled = true;
                            String artistName = currSong.substring(0, currSong.indexOf(" - "));
                            String trackName = currSong.substring(artistName.length() + 3);
                            Track.scrobble(artistName, trackName, (int) (System.currentTimeMillis() / 1000), session);
                        }
                    } else if (ps == PlayStatus.Paused) {
                        startTime = startTime.plus(Duration.between(startTime2, Instant.now()));
                    }
                    startTime2 = Instant.now();
                    Thread.sleep(16);
                }
            }
            Thread.sleep(1600);
        }
    }
}