    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.



# RELEASE NOTES - Miniseq.jar

Miniseq.jar is a piece of java software that allows the playback of several
midi sequences through a single midi output, simultaneously.

This port of Miniseq.jar is based on javax.midi from Oracle,
and runs on java platforms 100% compatible with Java Virtual machine from Oracle.

Miniseq.jar is currently released from and tested on the following platform only:

```
 Ubuntu 14.04
with
 java version "1.8.0_60"
 Java(TM) SE Runtime Environment (build 1.8.0_60-b27)
 Java HotSpot(TM) 64-Bit Server VM (build 25.60-b23, mixed mode)
```


## PROGRAMMATIC EXTENSION
=================================

### JAVA
---------------------------------
Please View the source files in package miniseq.

For a quick test, do the following steps in java:

    String song = "<cD<eC<gD<bB..<cD<eC<gD<bB";
    InputStream in = new ByteArrayInputStream(song.getBytes());
    Miniseq seq = new Miniseq();
    seq.play();
    seq.readFrom(new miniseq.FeatureInputStream(in));

Note that once miniseq is playing, you can feed new songs to the sequencer
by calling seq.readFrom() repeatedly, and the sequencer will start playing
them when requested.


### OTHER LANGUAGES
---------------------------------
Class miniseq/Main includes a simple tcp server application for programmatic
extension.  You can start the server by running:

java -cp Miniseq.jar miniseq/Main -server 12345

where 12345 sets the server port.

Said server accepts continuous tcp sessions one at a time. You can either create
a new connection to the server for each new song to be played, or let the
server wait on the client socket and send new fragments via same socket as they
become available.

Server input routine has a quick patch for http format, so songs can be sent
either as a raw byte stream (UTF-8), or in the message part of a http request.
In either case, the client must not expect a http answer from the server. The
client should do an automatic timeout when all data is sent. This enables the
server to respond to other requests, too.

For an example client application, see miniseq/Main.  Running (while server on):

java -cp Miniseq.jar miniseq/Main -client 12345 -seq cdefg

   or, for interactive mode:

java -cp Miniseq.jar miniseq/Main -client 12345


## UNDERSTANDING .seq SONG FORMAT
=================================
Miniseq comes with its own human-readable midi format, .seq.  An ingenious
format was chosen for two purposes: firstly, when midi is used to play
different kinds of effects of variable length, .midi resource files are
sometimes an overkill, while a simple command to play a series of notes 
suffices from the user's perspective; secondly, a custom song format
enables defining midi songs in a way that multiple songs can be played
simultaneously without necessarily breaking anything.

A .seq file is always text - miniseq reads songs in either UTF-8 or Latin-1,
with some restrictions.  For playback commands, the 7-bit ascii character set is
sufficient. If the song should include extended midi events - for example, text
events or comments - you should consider using UTF-8 as source file encoding.


## PARTS
---------------------------------
A .seq file consists of lines of notes.  A linefeed (hex 0x10) starts a new line.
Each line is treated as a track of midi data.  General midi channels are indexed
from 1 to 16; miniseq takes the programmatic point of view and indexes the same
channels from 0 to 15, or, in fact, from 0 to F hexadecimal notation.

It is possible to explicitly set the output midi channel in the beginning or
in the middle of a line, but it is usually best not to.  Instead, let the
sequencer decide which channel to use.  Standard drum channel
10(9) is an exception.

An example song could be defined in a file like this:

```
  x0 p0 cccedddfeeddc  /*(1)*/
  x9    f#*16
```

There are two parts fixed to midi channels 1(0) and 10(9). The first line plays
a short melody with GM Acoustic Grand Piano - midi program 0 - and the second
one plays a sequence of 16 quarter note ticks with the standard drum set in
channel 10(9).

This can be simplified a little by omitting channel selection for the first
part and letting miniseq pick up a channel:

```
     p0 cccedddfeeddc  /*(2)*/
  x9    f#*16
```

Furthermore, midi program selection can be omitted, because Grand Piano is
the default instrument:

```
        cccedddfeeddc  /*(3)*/
  x9    f#*16
```

Miniseq can be instructed to play another sequence simultaneously. (Hint: fire
miniseq.Main with parameter -client and with -file myfile.mseq)

```
     p105 g#6*16
```

This would play a series of banjo sounds in g sharp in the higher octave.
Selecting an octave for a note is optional and defaults to 5, which is the
middle octave.


## NOTES
---------------------------------
Notes are expressed in a modified European notation, where notes
c,d,e,f,g,a are commonly understood, h is one tone higher than a (in US
notation, b), and b is flattened h.  Sharpening a note is done by postfixing
as many #-characters as necessary.  Similarly, flattening is done with the 
character -.

Octaves are expressed from 0 to 9, where 5 is the default middle octave on
piano keyboard.

Finally, note length is expressed with letters from I to A (case sensitive).
Note length is attached to the note in much the same way as in sheet music.
Contrary to the midi file format, where note on and note off are separate midi
events.

The following table summarises note length notation:

```
  I  a 64th part of a whole note    (1/64)
  H  a 32th                         (1/32)
  G  a 16th                         (1/16)
  F  a 8th                          (1/8)
  E  a quarter note (the default)   (1/4)
  D  a half note                    (1/2)
  C  a whole note                   (1/1)
  B  a double note                  (2/1)
  A  a quadruple note               (4/1)
```

Note length indicators can be queued.

```
  cFF means the same as cE (1/4)
  cCCCC is the same as cA (4/1)
  eCD is a lengthened whole note (3/2), and just the same as eDC or eEEEEEE
```

Finally, note offset volume is expressed as v<XX>, where two hexadecimal digits
follow letter v. cv7F means middle c with velocity 127.  e#v40 means middle
e sharp with velocity 64 (the default). Incremental and decremental versions
v+<XX> and v-<XX> increment and decrement respectively the current default
offset volume by given amount.


## STOPS
---------------------------------
Where no note should be played, a stop is indicated with the letter . and
possible length modifiers.  .E means a quarter note stop, .C a stop for a whole
note - being the same as .... or .*4


## OTHER COMMANDS
---------------------------------
### TEMPO t

Playback tempo can be set in bpm, in the beginning of a sequence or in the
middle of it.  Miniseq will keep track of the tempi of simultaneously playing
sequences and keep them separate as long as their first tempo change events are
not of the same value.

```
t160.0 sets playback tempo to 160 beats per minute.
t200   sets playback tempo to 200 beats per minute.
```

### NOTE OFFSET VOLUME DEFAULT V

Default note offset volume can be set in the beginning or in the middle of a
sequence.

```
V7F sets default note offset volume to 127 (maximum).
V00 sets default note offset volume to 0 (inaudible).
```

### MIDI CHANNEL x

```
x0 selects midi channel 1 for following notes on the line.
xF selects midi channel 16.
```

### MIDI PROGRAM NUMBER p

```
p0 selects GM program number 0 (Acoustic Grand Piano)
p127 selects GM program number 127 (Gunshot - maximum)
```

### PLAY NOTE BUT DO NOT ADVANCE SONG/PART POSITION <

<c<eg  plays a chord (c major).  Notice that the last note is not prefixed.

### PLAY MULTIPLE NOTES THEN RETURN TO PREVIOUS POSITION [ ]

[ceg]bDE  plays a 3/2 note b and simultaneously notes c, e, g

### GENERIC MIDI EVENT !

To fire a generic midi event, use the ! notation:

!<midi event type|hex1><param1|hex2><param2|hex2>

!B0700  fires a control event (B) of type program volume change (07) with value
00 (silent)

!B0A7F  fires a control event (B) pan position 0A(10) with value 7F(127) - right

To fire a series of midi events with fixed interval, use !* notation:

!*B0A=00>7F,2,8  fires a series of pan position events, from initial value 0
(left) to final value 7F(127), with pan increment of 2 between two successive
events, and with interval 8*(1/64) (interval of 1/8 whole notes)


### RECORD INTO MEMORY, WHILE PLAYING BACK M

To record a sequence,

M1234 cdefg M

where 1234 is an arbitrary number for the recorded sequence.

To play back a previously recorded sequence on current channel,

N1234*4   (taken previous example and the same line or channel, this would
result in the same as writing cdefgcdefgcdefgcdefg


### RECORD INTO MEMORY FOR FUTURE USE R

To record a sequence without playing back simultaneously,

R01 ccccddd R

To play back,

R01


## EXAMPLE SEQUENCES
---------------------------------
```
t170 .B t100 . t170
        V08 R61cIc#6IaGR R62g#6GdHR .C  !*B0A=00>7F,2,8 N61*18 . N61*18 . N61*18

x9      !*B07=40>70,10,16  !B0A00
x9      M1 c5c5G*4.       c5*3.       ..Fc5*2.F       c5*3.       M N1*3
x9      M2 ..Fc#5*3     .Fc#5c#5G.Hc#5Hc#5.Fc#5*2.Fc#5    .Fc#5G.G...F M N2*3
x9      .F M3 e6v20e6v35 . M M31 N3 . M N31*7
x9      M4 .I f3#v15*6f3#v10I*16 .I*15 M N4*7
x9      M5 .B ...F.If4v25.I*15 M N5 M6 .F f4v30*2 ..F M N5 N6 N5
```



