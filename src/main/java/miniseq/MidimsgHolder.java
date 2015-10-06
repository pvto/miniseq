
package miniseq;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;

/** An instance should not be modified once created -
 * bean isolation assumed on contract. */
public class MidimsgHolder implements Comparable<MidimsgHolder>
{
    /** for wrapped midi message */
    public MidiMessage msg;
    /**  absolute offset in 32th parts of quarternotes */
    public long offset;
    /** alternative content in tempo change message */
    public Float newTempo;


    public MidimsgHolder() 
    {
    }
    public MidimsgHolder(long off, MidiMessage m)
    {
        offset = off;
        msg = m;
    }
    public MidimsgHolder(long offset, int cmd, int chn, int data1, int data2) throws InvalidMidiDataException
    {
        msg = new ShortMessage();
        ((ShortMessage)msg).setMessage(cmd, chn, data1, data2);
        this.offset = offset;
    }
    public int compareTo(MidimsgHolder t)
    {
        return (offset >= t.offset) ? 1 : -1;
    }
    /** constructs a tempo change message; bpm
     * */
    public static MidimsgHolder tempo(long offset, float tempo)
    {
        MidimsgHolder h = new MidimsgHolder();
        h.offset = offset;
        h.newTempo = tempo;
        return h;
    }
    /** constructs a midi text message */
    public static MidimsgHolder text(long offset, String text) throws InvalidMidiDataException
    {
        MetaMessage mi = new MetaMessage();
        byte[] bytes = text.getBytes();
        mi.setMessage(0x01, bytes, bytes.length);
        return new MidimsgHolder(offset, mi);
    }
}
