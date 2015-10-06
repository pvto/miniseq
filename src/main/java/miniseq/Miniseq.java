
package miniseq;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.TreeSet;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Timer;
import java.util.TimerTask;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import static javax.sound.midi.ShortMessage.*;


public class Miniseq {


    private Synthesizer synthesizer;
    private Receiver receiver;
    private List<PlayTask> playTasks;
    private List<Integer> unusedChannels; /// for deciding on which midi channel new lines are played on
    private boolean play = false;   // is sequencer playing?
    
    public void setSynthesizer(Synthesizer sy)
    {
        this.synthesizer = sy;
    }

    public Synthesizer getSynthesizer()
    {
        return synthesizer;
    }

    public void setReceiver(Receiver receiver)
    {
        this.receiver = receiver;
    }

    public Receiver getReceiver()
    {
        return receiver;
    }

    public boolean isPlaying()
    {
        return play;
    }
    
    
    public synchronized void init() throws MidiUnavailableException
    {
        if (unusedChannels != null)
            return;
        if (synthesizer == null)
            synthesizer = MidiSystem.getSynthesizer();
        if (!synthesizer.isOpen())
            synthesizer.open();
        MidiChannel[] chn = synthesizer.getChannels();
        playTasks = new ArrayList<PlayTask>();
        if (receiver == null)
            receiver = synthesizer.getReceiver();
        unusedChannels = new ArrayList<Integer>(chn.length);
        for(int i = 0; i < chn.length; i++)
            if (i != 9)
                unusedChannels.add(i);
    }

    public void play() throws MidiUnavailableException
    {
        if (play)
            return;
        play = true;
        if (playTasks == null)
            init();
        synchronized(playTasks)
        {
            for(PlayTask th : playTasks)
                th.timer.schedule(th.newTask(), 10L);
        }
    }

    public void pause()
    {
        if (!play)
            return;
        play = false;
        for(MidiChannel c : synthesizer.getChannels())
            if (c != null)
                c.allNotesOff();
    }

    public void stop()
    {
        play = false;
        synchronized(playTasks)
        {
            for(PlayTask th : playTasks)
                th.playQueue.clear();
        }
        for(MidiChannel c : synthesizer.getChannels())
            if (c != null)
                c.allNotesOff();
    }

    public boolean isPlayingNotes()
    {
        return play && playTasks.size() > 0 && playTasks.get(0).playQueue.size() > 0;
    }

    public void fadeout(int quarterNotes)
    {
        for(PlayTask th : playTasks)
            fadeout(th, quarterNotes);
    }
    
    private void fadeout(PlayTask th, int quarterNotes)
    {
        int off = 0;
        float subst = 1f / (quarterNotes * 32f);
        synchronized(th.playQueue)
        {
            Iterator<MidimsgHolder> i = th.playQueue.iterator();
            MidimsgHolder last = null;
            for(float factor = 1f; i.hasNext(); )
            {
                MidimsgHolder d = i.next();
                if (d.msg != null)
                {
                    byte[] m = d.msg.getMessage();
                    if ((m[0] & 0xFF) >> 4 == 0x09)
                    {
                        float fv = (float)(m[2] & 0xFF) * factor;
                        m[2] = (byte)fv;
                        ShortMessage m2 = new ShortMessage();
                        try {
                            m2.setMessage(NOTE_ON, m[0] & 0x0F, (int)(m[1] & 0xFF), (int)(m[2] & 0xFF));
                            d.msg = m2;
                        } catch(InvalidMidiDataException ex)
                        {
                            ex.printStackTrace();
                        }

                    }
                }
                if (last != null)
                    factor -= (d.offset - last.offset) * subst;
                if (factor < 0f)
                    factor = 0f;
                last = d;
            }
        }
    }

    private static class PlayNode extends TimerTask
    {
        private PlayNode(Runnable r)
        {
            this.run = r;
        }
        private Runnable run;
        @Override
        public void run() {
            run.run();
        }

    }
    /** holder for state information about a timer playing at a specific tempo.
     *  This is an instance class to access Miniseq members without ado.
     *  A PlayNode, a lightweight object, is constructed during each step of the
     * sequencer.
     */
    private class PlayTask implements Runnable
    {
        private long playOffset = 0L;
        // bpm
        private float tempo = 160.0f;
        // queue of events to play
        private ArrayDeque<MidimsgHolder> playQueue = new ArrayDeque<MidimsgHolder>();;
        // for fragments defined in the input sequence
        private Map<Integer, TreeSet<MidimsgHolder>> seqmem = new HashMap<Integer, TreeSet<MidimsgHolder>>(); 
        private Timer timer = new Timer();

        private TimerTask newTask()
        {
            return new PlayNode(this);
        }

        @Override
        public void run()
        {
            MidimsgHolder msg = null;
            if (playQueue.size() > 0)
            {
                synchronized(playQueue)
                {
                    msg = playQueue.getFirst();
                }
            }
            while(msg != null && msg.offset <= playOffset)
            {
                if (msg.newTempo != null)
                {
                    tempo = msg.newTempo;
                }
                else
                {
                    if (msg.offset == playOffset)
                        receiver.send(msg.msg, -1);
                }
                synchronized(playQueue)
                {
                    playQueue.removeFirst();
                    if (playQueue.size() > 0)
                        msg = playQueue.getFirst();
                    else
                        msg = null;
                }
            }
            playOffset++;
            synchronized(playQueue)
            {
                if (playQueue.size() == 0)
                {
                    playTasks.remove(this);
                    return;
                }
            }
            if (play) // continue to play if not paused meanwhile
            {
                timer.schedule(newTask(), (long)(60000f / tempo / 32f));
            }
        }
    }
    
    private static int[] baseNote = new int[] // from middle C = 60
        // c   d   e   f   g   h   a   b
        { 60, 62, 64, 65, 67, 71, 69, 70, 71, 73, 75};

    public void readFrom(FeatureInputStream in) throws IOException, InvalidMidiDataException
    {
        readFrom(in, 0L);
    }
    
    public void readFrom(FeatureInputStream in, long startOffset) throws IOException, InvalidMidiDataException
    {
        PlayTask th = new PlayTask();
        float tempo = 160f;
        char ch = in.readChar();
        TreeSet<MidimsgHolder> lst = new TreeSet<MidimsgHolder>();
        while (ch != 0xFFFF)
        {
            long dstoff = 0L;
            int channel;
            synchronized(unusedChannels)
            {
                channel = unusedChannels.remove(0); // channel for new notes
                unusedChannels.add(channel);
            }
            int vol = 64;   // base volume for new notes (0..127)
            long pushOffset = 0;
            TreeSet<MidimsgHolder> memslot = null;
            long memOnly = -1;
            while (ch != '\n' && ch != 0xFFFF)
            {
                boolean incOffset = true;
                switch(ch)
                {
                    case '\\':
                        if (in.peekChar() == '\r') in.readChar();
                        if (in.peekChar() == '\n') in.readChar();
                        break;
                    case '/':
                        if (in.peekChar() == '*') {  /* checks and reads for this kind of comment block */
                            in.readChar();
                            for(;;)
                            {
                                while ((ch = in.readChar()) != '*' && ch != 0xFFFF);
                                if ((ch = in.readChar()) == '/' || ch == 0xFFFF)
                                    break;
                            }
                        }
                        break;
                    case 'x':
                        synchronized(unusedChannels)
                        {
                            unusedChannels.remove(channel);
                            unusedChannels.add(0, channel);
                        }
                        channel = in.readInt(1, 16);
                        break;
                    case 't':
                        MidimsgHolder t = MidimsgHolder.tempo(dstoff, in.readFloat());
                        if (tempo == 160f)
                            tempo = t.newTempo;
                        if (memOnly == -1)
                            lst.add(t);
                        if (memslot != null)
                            memslot.add(t);
                        break;
                    case 'V': vol = Math.min(in.readInt(2,16), 127);  break;
                    case 'R':
                        if (memOnly == -1)
                        {
                            memOnly = dstoff;
                            th.seqmem.put(in.readInt(), memslot = new TreeSet<MidimsgHolder>());
                        }
                        else
                        {
                            memslot.add(MidimsgHolder.text(dstoff, "REPEAT"));
                            memslot = null;
                            dstoff = memOnly;
                            memOnly = -1;
                        }
                        break;
                    case 'M':
                        if (memslot == null)
                            th.seqmem.put(in.readInt(), memslot = new TreeSet<MidimsgHolder>());
                        else {
                            memslot.add(MidimsgHolder.text(dstoff, "REPEAT"));
                            memslot = null;
                        }
                        break;
                    case 'p':
                        MidimsgHolder pc = new MidimsgHolder(dstoff, PROGRAM_CHANGE, channel, in.readInt(), 0);
                        if (memOnly == -1)
                            lst.add(pc);
                        if (memslot != null)
                            memslot.add(pc);
                        break;
                    case '!': // generic midi event
                        int tmp = in.peekChar();
                        if (tmp == '*') // fade
                            in.readChar();
                        Integer a = null, b = 1, step = 1, delta = 128;
                        int eventType = in.readInt(1, 16) << 4;
                        int param1 = in.readInt(2, 16);
                        if (tmp == '*') // fade
                        {
                            in.readChar();  // =
                            a = in.readInt(2, 16);
                            in.readChar();  // >
                            b = in.readInt(2, 16);
                            try {
                                step = in.readInt();
                            } catch(IOException e) { };
                            try {
                                delta = in.readInt();
                            } catch(IOException e) { };
                        } else
                        {
                            a = in.readInt(2, 16);
                            b = a;
                        }
                        int d = 0;
                        for (int x = a; step < 0 ? x >= b : x <= b; x += step, d += delta)
                        {
                            MidimsgHolder hh = new MidimsgHolder(dstoff + d, eventType, channel, param1, x);
                            if (memOnly == -1)
                                lst.add(hh);
                            if (memslot != null)
                                memslot.add(hh);
                        }
                        break;
                    case '.':  // stop
                        if (memslot != null && memslot.size() == 0)
                            memslot.add(MidimsgHolder.text(dstoff, ".")); // record correct start moment
                        int length = readLength(in);
                        if (in.peekChar() == '*')
                        {
                            in.readChar();
                            int qty2 = in.readInt();
                            for(int i = 0; i < qty2; i++)
                                dstoff += length;
                        } else
                            dstoff += length;
                        break;
                    case '[':
                        pushOffset = dstoff;
                        break;
                    case ']':
                        dstoff = pushOffset;
                        break;
                    case '<':
                        incOffset = false;
                        ch = in.readChar();
                        if (ch < 'c' || ch > 'h')
                            break;
                    case 'c':  case 'd':  case 'e':  case 'f':  case 'g':  case 'a':  case 'b':  case 'h':
                        int add = (ch=='a'||ch=='b') ? 'c' - 8 : 'c';
                        int base = baseNote[ch - add];
                        int dur = 0;
                        int velo = vol;
                        int qty = 1;
                        boolean ok = true;
                        while (ok) {
                            int mdf = in.peekChar();
                            switch(mdf) {
                                case '#': base++;  in.readChar();  break;
                                case '-': base--;  in.readChar();  break;
                                case 'v': in.readChar();  
                                    if (in.peekChar() == '+')
                                    {
                                        in.readChar();
                                        velo = in.readInt(2,16);
                                        vol = Math.min(vol + velo, 127);
                                        velo = vol;
                                        break;
                                    }
                                    else if (in.peekChar() == '-')
                                    {
                                        in.readChar();
                                        velo = in.readInt(2,16);
                                        vol = Math.max(vol - velo, 0);
                                        velo = vol;
                                        break;
                                    }
                                    velo = Math.min(in.readInt(2,16), 127);  break;
                                case '0':  case '1':  case '2':  case '3':  case '4':  case '5':  case '6':  case '7':  case '8':  case '9':
                                    base += (mdf - '5') * 12;  
                                    in.readChar();
                                    break;
                                case 'I':  case 'H':  case 'G':  case 'F':  case 'E':  case 'D':  case 'C':  case 'B':  case 'A':
                                    dur += readLength(in);
                                    break;
                                case '*': in.readChar();  qty = in.readInt();  break;
                                default: 
                                    ok = false;
                                    break;
                            }
                        }
                        if (dur == 0)
                            dur = 32; // 32 ticks for 4/4
                        for(int i = 0; i < qty; i++)
                        {
                            if (memOnly == -1)
                            {
                                lst.add(new MidimsgHolder(dstoff, NOTE_ON, channel, base, velo));
                                lst.add(new MidimsgHolder(dstoff + dur, NOTE_OFF, channel, base, 0));
                            }
                            if (memslot != null)
                            {
                                memslot.add(new MidimsgHolder(dstoff, NOTE_ON, channel, base, velo));
                                memslot.add(new MidimsgHolder(dstoff + dur, NOTE_OFF, channel, base, 0));
                            }
                            dstoff += dur;
                        }
                        if (!incOffset)
                            dstoff -= dur * qty;
                        break;
                    case 'N':
                        TreeSet<MidimsgHolder> stored = th.seqmem.get(in.readInt());
                        int n = 1;
                        if (in.peekChar() == '*')
                        {
                            in.readChar();
                            n = in.readInt();
                        }
                        if (stored != null)
                        {
                            for(int i = 0; i < n; i++)
                            {
                                long diff = dstoff - stored.first().offset;
                                for(MidimsgHolder h : stored)
                                {
                                    MidimsgHolder h2 = new MidimsgHolder();
                                    h2.offset = h.offset + diff;
                                    h2.msg = h.msg;
                                    h2.newTempo = h.newTempo;
                                    if (memOnly == -1)
                                        lst.add(h2);
                                    if (memslot != null)
                                        memslot.add(h2);
                                }
                                dstoff += stored.last().offset - stored.first().offset;
                            }
                        }
                        break;
                }
                ch = in.readChar();
            }
            ch = in.readChar();
        }
        PlayTask target = th;
        synchronized(playTasks)
        {
            for(PlayTask test : playTasks)
                if (test.tempo == tempo)
                {
                    target = test;
                    target.seqmem.putAll(th.seqmem);
                }
        }
        if (target == th) // must start a new timer
        {
            synchronized(playTasks)
            {
                playTasks.add(target);
            }
            target.timer.schedule(target.newTask(), 10L);
        }
        long offset = target.playOffset + startOffset;
        for(MidimsgHolder d : lst)
            d.offset = offset + d.offset + 32L;
        if (target.playQueue.isEmpty())
        {
            synchronized(target.playQueue)
            {
                target.playQueue.addAll(lst);
            }
        }
        else
        {
            synchronized(target.playQueue)
            {
                lst.addAll(target.playQueue);
                target.playQueue.clear();
                target.playQueue.addAll(lst);
            }
        }
    }

    private int readLength(FeatureInputStream in) throws IOException {
        int ret = 32;   // 32 ticks for quarter note
        int tmp = in.peekChar();
        switch((char)tmp)
        {
            case 'I':  ret >>= 4;  in.readChar();  break; // 1/64
            case 'H':  ret >>= 3;  in.readChar();  break; // 1/32
            case 'G':  ret >>= 2;  in.readChar();  break; // 1/16
            case 'F':  ret >>= 1;  in.readChar();  break; // 1/8
            case 'E':  in.readChar(); break;              // 1/4
            case 'D':  ret <<= 1;  in.readChar();  break; // 1/2
            case 'C':  ret <<= 2;  in.readChar();  break; // 1/1
            case 'B':  ret <<= 3;  in.readChar();  break; // 2/1
            case 'A':  ret <<= 4;  in.readChar();  break; // 4/1
        }
        return ret;
    }

    private long cc = 0L;




}
