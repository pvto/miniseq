package miniseq;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;

public class Main {

    private static void printHelp(PrintStream out)
    {
        out.println("========================================================");
        out.println(" Miniseq            (c) Copyleft  Paavo Toivanen 2011");
        out.println("    GNU GPL   -  http://www.gnu.org/copyleft/gpl.html");
        out.println("");
        out.println("Usage:  Miniseq [-file <filename> | -seq <sequence> | -server <port>]");
        out.println(" (Hint:   try:  Miniseq -seq \"ceg.c6e6g6D\")");
        out.println("    or:   try:  Miniseq -seq \"cba-.c6e6g6v30\\p100<cB<eB<g#B\\x9f#3*8\"");
        out.println("");
        out.println(" Server example:  1)  Miniseq -server 12345");
        out.println("                  2)  curl -m2 --data-ascii \"ceg\" \"http://localhost:12345\"");
        out.println("  (you need to install curl for sending request 2");
        out.println("                    . . .");
        out.println(" Now playing an example melody through midi");
        out.println("                    . . .");
    }

    public static void main(String[] args) throws MidiUnavailableException, IOException, InvalidMidiDataException
    {
        InputStream in = null;
        int server = 0;
        String host = null;
        int hostPort = 33333;
        for (int i = 0; i < args.length; i++)
        {
            if ("-file".equals(args[i]))
            {
                in = new FileInputStream(args[++i]);
            }
            else if ("-seq".equals(args[i]))
            {
                in = new ByteArrayInputStream(args[++i].getBytes());
            }
            else if ("-server".equals(args[i]))
            {
                try
                {
                    server = Integer.parseInt(args[++i]);
                }
                catch (NumberFormatException ex)
                {
                    System.out.println("Illegal server port: " + args[i]);
                    return;
                }
                catch (ArrayIndexOutOfBoundsException ex)
                {
                    System.out.println("Usage: -server <port>");
                    return;
                }
            }
            else if ("-client".equals(args[i]))
            {
                try
                {
                    host = args[++i];
                    int ind = host.indexOf(':');
                    if (ind > 0)
                    {
                        hostPort = Integer.parseInt(host.substring(ind + 1));
                        host = host.substring(0, ind);
                    }
                }
                catch (ArrayIndexOutOfBoundsException ex)
                {
                    System.out.println("Usage: -client <host:port>");
                }
            }
            else
            {
                System.out.println("  Unknown argument: " + args[i]);
            }
        }

        if (server != 0)
        {
            runServer(server);
            return;
        }
        if (host != null)
        {
            runClient(host, hostPort, in);
            return;
        }
        if (in == null)
        {
            printHelp(System.out);
            String song =
                    "    <cD<eC<gD<bB..<cD<eC<gD<bB....<cD<eC<gD<bB\n"
                    + "p23 cegcegc6e6g6B .A cegcegc6e6g6B\n"
                    + "p10 c*8.Ba3.c4.a3F.Fc4F.Ba3.c4.a3F.Fc4F.Bc*8.Ba3.c4.a3F.Fc4F.Ba3.c4.a3F.Fc4D.";
            in = new ByteArrayInputStream(song.getBytes());
        }

        Miniseq seq = new Miniseq();
        seq.play();
        seq.readFrom(new FeatureInputStream(in));

        while (seq.isPlayingNotes())
        {
            try
            {
                Thread.sleep(100);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        System.exit(0);
    }
    private static ExecutorService exs = Executors.newSingleThreadExecutor();

    private static void runServer(int server) throws IOException, MidiUnavailableException
    {
        Miniseq seq = new Miniseq();
        seq.play();

        ServerSocket serv = new ServerSocket(server);
        System.out.println("ServerSocket bound: " + server);
        byte[] okbytes =
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n".getBytes();
        while (true)
        {
            Socket s = null;
            OutputStream sout = null;
            try
            {
                s = serv.accept();
                final Socket sf = s;
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final boolean[] finished =
                {
                    false
                };
                Runnable r = new Runnable() {

                    public void run()
                    {
                        System.out.println("Got connection: " + sf.getInetAddress().toString());
                        InputStream zin = null;
                        try
                        {
                            zin = sf.getInputStream();
                            byte[] bytes = new byte[4096];
                            int i = 0;
                            int start;
                            while (sf.isConnected() && i >= 0)
                            {
                                start = 0;
                                if ((i = zin.read(bytes)) > 0)
                                {
                                    byte[] t1 = "HTTP".getBytes();
                                    int t2 = 0;
                                    for (int j = 0; j < i; j++)
                                    {
                                        if (bytes[j] == t1[t2])
                                        {
                                            if (t2 == 3)
                                            {
                                                if (t1[0] == 'H')
                                                {
                                                    t1 = "\r\n\r\n".getBytes();
                                                }
                                                else
                                                {
                                                    start = j + 1;
                                                }
                                                t2 = 0;
                                            }
                                            else
                                            {
                                                t2++;
                                            }
                                        }
                                        else
                                        {
                                            t2 = 0;
                                        }
                                    }
                                    out.write(bytes, start, i);
                                    synchronized (out)
                                    {
                                        out.notify();
                                    }
                                    synchronized (out)
                                    {
                                        out.wait();
                                        out.reset();
                                    }
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        finally
                        {
                            System.out.println("exit reader");
                            try
                            {
                                zin.close();
                            }
                            catch (Exception e)
                            {
                            }
                            finished[0] = true;
                            synchronized (out)
                            {
                                out.notify();
                            }
                        }
                    }
                };
                exs.submit(r);
                while (!finished[0])
                {
                    byte[] play = null;
                    synchronized (out)
                    {
                        out.wait();
                        if (out.size() > 0)
                        {
                            play = out.toByteArray();
                        }
                        out.notify();
                    }
                    if (play != null && play.length > 0)
                    {
                        System.out.println("Playing: " + new String(play));
                        seq.readFrom(new FeatureInputStream(new ByteArrayInputStream(play)));
                        play = null;
                    }
                }
                System.out.println("close connection");
                if (!s.isClosed())
                {
                    sout = s.getOutputStream();
                    sout.write(okbytes);
                    System.out.println("Wrote response");
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                if (s != null)
                {
                    try
                    {
                        s.close();
                    }
                    catch (Exception e)
                    {
                    }
                }
            }
        }
    }

    private static void runClient(String host, int hostPort, InputStream in) throws IOException
    {
        Socket s = new Socket(host, hostPort);
        s.setSoTimeout(500);
        InputStream zin = s.getInputStream();
        OutputStream zout = s.getOutputStream();
        if (in == null)
        {
            in = System.in;
        }
        else
        {
            byte[] b = new byte[65536];
            int i = in.read(b);
            zout.write(b, 0, i);
            zout.close();
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = r.readLine()) != null)
        {
            zout.write(line.getBytes());
            zout.flush();
            int ch;
            try
            {
                while ((ch = zin.read()) != -1)
                {
                    System.out.print((char) ch);
                }
            }
            catch (java.net.SocketTimeoutException se)
            {
                // ok
            }
            System.out.println(">");
        }
    }
}
