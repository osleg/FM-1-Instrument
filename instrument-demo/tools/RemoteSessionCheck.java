/* SPDX-License-Identifier: GPL-3.0-only */
import org.fm1.companion.protocol.OpenSession;
import org.fm1.companion.protocol.Wire6;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayDeque;

/** Android's actual sender through firmware USB mailbox, cache, renderer and input scanner. */
public final class RemoteSessionCheck {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        boolean compact = args.length > 1 && args[1].equals("compact");
        boolean fallback = args.length > 1 && args[1].equals("fallback");
        Process peer = new ProcessBuilder(args[0]).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        DataOutputStream out = new DataOutputStream(peer.getOutputStream());
        DataInputStream in = new DataInputStream(peer.getInputStream());
        ArrayDeque<byte[]> queue = new ArrayDeque<>();
        short[] screen = new short[57600];
        for (int i = 0; i < screen.length; i++) {
            int tile = i / (240 * 16) * 15 + (i % 240) / 16;
            // Exercise runs, literals, raw fallback, and more distinct tiles than cache slots.
            screen[i] = (short)(tile % 2 == 0 && i % 16 < 12 ? tile * 257 : i * 113 + i / 240);
        }
        int[] ready = {0}, scans = {0}, keys = {0}, remoteCommands = {0};
        OpenSession session = new OpenSession(new OpenSession.Host() {
            public void send(byte[] frame) {
                if (frame[5] == Wire6.REMOTE) {
                    check(compact || frame[10] < 5, "compact commands require negotiated support");
                    remoteCommands[0]++;
                }
                queue.add(frame);
            }
            public byte[] snapshot() { throw new AssertionError("remote mode requested native state"); }
            public short[] remoteFrame() { return screen; }
            public boolean input(int serial, byte[] scan) { scans[0]++; keys[0] = Wire6.u28(scan,2); return true; }
            public void reset() {}
            public void synchronizedDisplay(int audio, boolean transport) { ready[0]++; }
            public void stopped(String reason) { throw new AssertionError(reason); }
        }, "test.remote", Wire6.PHONE, compact || fallback ? new Wire6.Capability[]{
                new Wire6.Capability(1,1,0,true), new Wire6.Capability(16,1,0,true),
                new Wire6.Capability(Wire6.REMOTE_COMPACT,1,0,compact)
        } : new Wire6.Capability[]{new Wire6.Capability(1,1,0,true), new Wire6.Capability(16,1,0,true)});
        session.begin(317,0);
        int matches = 0, lastHash = 0, dropped = 0, idleAt = -1, idleCommands = 0, initialCommands = 0;
        boolean edited = false, keyTest = false;
        try {
            for (int now = 1; now < 10000; now++) {
                session.tick(now);
                out.writeInt(now); out.writeInt(3); out.writeInt(keyTest ? 1 : 0);
                out.writeInt(0); out.writeInt(0); out.writeInt(queue.size());
                while (!queue.isEmpty()) { byte[] frame = queue.remove(); out.writeInt(frame.length); out.write(frame); }
                out.flush();
                in.readInt(); int phase = in.readInt(); in.readInt(); in.readInt();
                int length = in.readInt(); check(length >= 0 && length <= 4096,"reply bound");
                byte[] reply = new byte[length]; in.readFully(reply);
                in.readInt(); int view = in.readInt(); check(view == 0,"remote must not install native view");
                lastHash = in.readInt(); in.readInt();
                final int clock = now;
                final int[] drop = {dropped};
                Wire6.Decoder decoder = new Wire6.Decoder(frame -> {
                    if (drop[0] == 0 && frame.command == Wire6.ACK && frame.payload[0] == Wire6.REMOTE) {
                        drop[0]++; return; // Retry must not corrupt cache/coverage.
                    }
                    session.receive(frame, clock);
                });
                decoder.feed(reply,0,reply.length); dropped = drop[0];
                if (ready[0] == 1 && lastHash == hash(screen)) {
                    matches++;
                    if (!edited) {
                        initialCommands = remoteCommands[0];
                        screen[0] ^= 0x1234; screen[57599] ^= 0x7c00; edited = true;
                    }
                    else if (!keyTest) keyTest = true;
                    else if (keys[0] == 1 && matches > 5) {
                        if (idleAt < 0) { idleAt = now; idleCommands = remoteCommands[0]; }
                        if (now - idleAt >= 2200) break;
                    }
                }
                check(phase <= 2,"valid controller phase");
            }
            check(ready[0] == 1 && dropped == 1,"one synchronized display after ACK loss");
            check(lastHash == hash(screen),"actual LCD matches RGB565 full frame and incremental update");
            check(keys[0] == 1 && scans[0] > 1,"physical key reaches remote host");
            check(idleAt >= 0 && idleCommands == remoteCommands[0],"idle lease survives without repainting unchanged pixels");
            if (compact) check(initialCommands < 1300, "compact transfer reduces USB exchanges");
            System.out.printf("PASS: %s remote screen, cache eviction, ACK retry, RGB565, incremental update, input and idle lease (%d initial commands)%n",
                    compact ? "compact" : "original", initialCommands);
        } finally { out.close(); check(peer.waitFor() == 0,"peer exit"); in.close(); }
    }
    static int hash(short[] pixels) {
        int h = 0x811c9dc5;
        for (short pixel : pixels) { h = (h ^ ((pixel >>> 8) & 255)) * 16777619; h = (h ^ (pixel & 255)) * 16777619; }
        return h;
    }
}
