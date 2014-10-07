package com.example.audiox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("UseSparseArrays") public class MainActivity extends Activity implements OnClickListener{

    private static final int chooseVid=1;
    private static final String TAG="MainActivity";
    //private static final boolean VERBOSE=false;
    //private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //SET UP BUTTON CLICK LISTENERS
        ((Button)findViewById(R.id.chooseVid)).setOnClickListener(this);

    }

    public void onActivityResult(int requestCode,int resultCode,Intent data)
    {
        if(resultCode==RESULT_OK)
        {
            if(requestCode==chooseVid)
            {
                Uri uri=data.getData();
                String path=getPath(uri);
                Toast.makeText(getApplicationContext(), path, Toast.LENGTH_LONG).show();
                Log.v(TAG, "Path % - "+ path);
                createNewFolder();

                boolean res=false;
                try {
                    res=testAudioOnly(path);
                } catch (Exception e) {
                    e.printStackTrace();
                }
    			/*
                if(res)
    			{
    				TextView tv=(TextView)findViewById(R.id.audioResultsTextView);
    				tv.setVisibility(1);
    				tv.setText("Success");
    			}
    			else
    			{
    				TextView tv=(TextView)findViewById(R.id.audioResultsTextView);
    				tv.setVisibility(1);
    				tv.setText("Failed");
    			}
    			*/
            }
        }
    }

    private String getPath(Uri uri)
    {
        String[] projection={MediaStore.Video.Media.DATA};
        Cursor cursor=null;
        String result="Error";
        try
        {
            cursor=getContentResolver().query(uri,projection,null,null,null);
            int column_index=cursor.getColumnIndex(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            result=cursor.getString(column_index).toString();
        }
        finally
        {
            if(cursor!=null)
                cursor.close();
        }
        return result;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void createNewFolder()
    {
        String targetPath=Environment.getExternalStorageDirectory().toString()+"/VidEx";
        File file = new File(targetPath);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Toast.makeText(getApplicationContext(), "Cannot access external storage.", Toast.LENGTH_LONG).show();
            }
            else
            {
                TextView tv=(TextView)findViewById(R.id.audioResultsTextView);
                tv.setVisibility(1);
                tv.setText(targetPath);
            }
        }
        else
        {
            TextView tv=(TextView)findViewById(R.id.audioResultsTextView);
            tv.setVisibility(1);
            tv.setText(targetPath);
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId())
        {
            case R.id.chooseVid:	Intent intent=new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,chooseVid);
                break;
        }

    }

    public boolean testAudioOnly(String source) throws Exception {
        String outputFile = "/storage/emulated/0/VidEx/audioOnly"+new Date().getTime()+".mp4";
        boolean s=false;
        try
        {
            s=cloneMediaUsingMuxer(source, outputFile, 1, -1);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return s;
    }

    private void decodeLoop(MediaExtractor extractor ,MediaFormat format)
    {

        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        String mime = format.getString(MediaFormat.KEY_MIME);

        // the actual decoder
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();

        // get the sample rate to configure AudioTrack
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        // create our AudioTrack instance
       /*
        AudioTrack audioTrack = new AudioTrack(
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize (
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT
                ),
                AudioTrack.MODE_STREAM
        );

        // start playing, we will feed you later
        audioTrack.play();
        */
        //extractor.selectTrack(0);

        // start decoding
        final long kTimeOutUs = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int noOutputCounter = 0;
        int noOutputCounterLimit = 50;

        //previously global varibale
        Boolean doStop = false;
        int inputBufIndex;
        int bufIndexCheck = 0;
        //int lastInputBufIndex;

        while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit && !doStop) {
            //Log.i(LOG_TAG, "loop ");
            noOutputCounter++;

            if (!sawInputEOS) {

                inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);

                bufIndexCheck++;

                Log.d(TAG, " bufIndexCheck " + bufIndexCheck + " inputBufIndex " + inputBufIndex);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                    int sampleSize =
                            extractor.readSampleData(dstBuf, 0 /* offset */);

                    long presentationTimeUs = 0;

                    Log.d(TAG, " sampleSize " + sampleSize + " inputBufIndex " + inputBufIndex);

                    if (sampleSize < 0) {
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    // can throw illegal state exception (???)

                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    Log.d(TAG, " sawInputEOS " + sawInputEOS + " presentationTimeUs " + presentationTimeUs);


                    if (!sawInputEOS) {
                        extractor.advance();
                    }
                }
                else
                {
                    Log.e(TAG, "inputBufIndex " +inputBufIndex);
                }
            }

            /*

            int res = codec.dequeueOutputBuffer(info, kTimeOutUs);



            if (res >= 0) {
                //Log.d(LOG_TAG, "got frame, size " + info.size + "/" + info.presentationTimeUs);
                if (info.size > 0) {
                    noOutputCounter = 0;
                }

                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                final byte[] chunk = new byte[info.size];
                buf.get(chunk);
                buf.clear();
                if(chunk.length > 0){
                    audioTrack.write(chunk,0,chunk.length);
                    if(this.mState != State.Playing)
                    {
                        mDelegateHandler.onRadioPlayerPlaybackStarted(MP3RadioStreamPlayer.this);
                    }
                    this.mState = State.Playing;
                }
                codec.releaseOutputBuffer(outputBufIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(LOG_TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();

                Log.d(LOG_TAG, "output buffers have changed.");
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = codec.getOutputFormat();

                Log.d(LOG_TAG, "output format has changed to " + oformat);
            } else {
                Log.d(LOG_TAG, "dequeueOutputBuffer returned " + res);
            }
            */
        }

        Log.e(TAG, "inputBuf length " + codecInputBuffers.length);

        /*
        Log.d(LOG_TAG, "stopping...");

        relaxResources(true);

        this.mState = State.Stopped;
        doStop = true;

        // attempt reconnect
        if(sawOutputEOS)
        {
            try {
                MP3RadioStreamPlayer.this.play();
                return;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        */
    }

    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private static final boolean VERBOSE = true;

    private boolean cloneMediaUsingMuxer(String srcMedia, String dstMediaPath,
                                      int expectedTrackCount, int degrees) throws IOException {
        // Set up MediaExtractor to read from the source.
        //AssetFileDescriptor srcFd = mResources.openRawResourceFd(srcMedia);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcMedia);
        int trackCount = extractor.getTrackCount();
        //assertEquals("wrong number of tracks", expectedTrackCount, trackCount);
        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(dstMediaPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        // Set up the tracks.
        int audioTrackIndex = 0;
        //HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            extractor.selectTrack(i);
            MediaFormat format = extractor.getTrackFormat(i);
            String COMPRESSED_AUDIO_FILE_MIME_TYPE = format.getString(MediaFormat.KEY_MIME);

            if(COMPRESSED_AUDIO_FILE_MIME_TYPE.startsWith("audio/")) {
                audioTrackIndex = muxer.addTrack(format);
            }
            //indexMap.put(i, dstIndex);
        }
        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int frameCount = 0;
        int offset = 100;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        if (degrees >= 0) {
            muxer.setOrientationHint(degrees);
        }

        muxer.start();
        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);
            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.flags = extractor.getSampleFlags();
                int trackIndex = extractor.getSampleTrackIndex();
                if(audioTrackIndex == trackIndex ) {
                    muxer.writeSampleData(trackIndex, dstBuf,
                            bufferInfo);
                }
                extractor.advance();
                frameCount++;
                if (VERBOSE) {
                    Log.d(TAG, "Frame (" + frameCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(KB) " + bufferInfo.size / 1024);
                }
            }
        }
        muxer.stop();
        muxer.release();
        //srcFd.close();
        return true;
    }


    private boolean cloneMediaUsingMuxerOld(String filePath, String dstMediaPath,int expectedTrackCount, int degrees) throws IOException
    {

        String COMPRESSED_AUDIO_FILE_MIME_TYPE = null;

        int COMPRESSED_AUDIO_FILE_BIT_RATE = 128000;

        int SAMPLING_RATE = 0;

        int channels = 0;
        long duration = 0;
        boolean suc=false;

        //displaying the video path
        TextView tv=(TextView)findViewById(R.id.videoFilePathTextView);
        tv.setVisibility(1);
        tv.setText(filePath);

        //created the MediaExtractor and Set source path
        MediaExtractor extractor;
        extractor = new MediaExtractor();
        extractor.setDataSource(filePath);

        int numTracks = extractor.getTrackCount();

        File outputFile = new File(dstMediaPath);

        if (outputFile.exists())
            outputFile.delete();

        MediaMuxer mux = null;

        for (int i = 0; i < numTracks; ++i) {

            MediaFormat format = extractor.getTrackFormat(i);
            COMPRESSED_AUDIO_FILE_MIME_TYPE = format.getString(MediaFormat.KEY_MIME);

            if(COMPRESSED_AUDIO_FILE_MIME_TYPE.startsWith("audio/")) {

                mux = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                SAMPLING_RATE = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                duration = format.getLong(MediaFormat.KEY_DURATION);

                MediaFormat outputFormat = MediaFormat.createAudioFormat(COMPRESSED_AUDIO_FILE_MIME_TYPE, SAMPLING_RATE, 1);
                //outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                //outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, COMPRESSED_AUDIO_FILE_BIT_RATE);

                int audioTrackIdx = mux.addTrack(outputFormat);

                mux.start();

                extractor.selectTrack(i);

                //decodeLoop(extractor , format);

                //openmxplayer style

                MediaCodec codec;

                // create the actual decoder, using the mime to select
                codec = MediaCodec.createDecoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE);
                // check we have a valid codec instance
                if (codec == null) {

                    Log.d(TAG, "codec is null.. :-( ");
                    return false;
                }

                codec.configure(format, null, null, 0);
                codec.start();
                ByteBuffer[] codecInputBuffers = codec.getInputBuffers();
                ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();


                // start decoding
                final long kTimeOutUs = 1000;
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;
                int noOutputCounter = 0;
                int noOutputCounterLimit = 10;
                boolean stop = false;
                long presentationTimeUs = 0;

                // state.set(PlayerStates.PLAYING);
                while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit && !stop) {


                    noOutputCounter++;
                    // read a buffer before feeding it to the decoder
                    if (!sawInputEOS) {
                        int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
                        if (inputBufIndex >= 0) {
                            ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                            int sampleSize = extractor.readSampleData(dstBuf, 0);
                            if (sampleSize < 0) {
                                Log.d(TAG, "saw input EOS. Stopping playback");
                                sawInputEOS = true;
                                sampleSize = 0;
                            } else {
                                presentationTimeUs = extractor.getSampleTime();
                                final int percent = (duration == 0) ? 0 : (int) (100 * presentationTimeUs / duration);
                                Log.d(TAG, "percent " + percent + " presentationTimeUs " + presentationTimeUs);
                            }

                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                            if (!sawInputEOS) extractor.advance();

                        } else {
                            Log.e(TAG, "inputBufIndex " + inputBufIndex);
                        }
                    } // !sawInputEOS

                    // decode to PCM and push it to the AudioTrack player
                    int res = codec.dequeueOutputBuffer(info, kTimeOutUs);

                    if (res >= 0) {
                        if (info.size > 0) noOutputCounter = 0;

                        int outputBufIndex = res;
                        //ByteBuffer buf = codecOutputBuffers[outputBufIndex];
                        mux.writeSampleData(audioTrackIdx, codecOutputBuffers[outputBufIndex], info);

                        Log.d(TAG, " info.size = " + info.size);


                        codec.releaseOutputBuffer(res, false);

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                            Log.d(TAG, "saw output EOS.");
                            sawOutputEOS = true;

                        }

                    } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        codecOutputBuffers = codec.getOutputBuffers();
                        Log.d(TAG, "output buffers have changed.");
                    } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat oformat = codec.getOutputFormat();
                        Log.d(TAG, "output format has changed to " + oformat);
                    } else {
                        Log.d(TAG, "dequeueOutputBuffer returned " + res);
                    }
                }


                Log.d(TAG, "stopping...");

                if (codec != null) {
                    codec.stop();
                    codec.release();
                    codec = null;
                }

              /*  if(mux != null) {
                    mux.stop();
                    mux.release();
                }*/

                //we are interested only on one audio track.. So we are breaking out of loop
                break;
            }

            String audioInfo = "Track info: mime:" + COMPRESSED_AUDIO_FILE_MIME_TYPE + " sampleRate:" + SAMPLING_RATE + "" +
                    " channels:" + channels + " bitrate:" + COMPRESSED_AUDIO_FILE_BIT_RATE + " duration:" + duration;
            Log.d(TAG, audioInfo);

            TextView tv1 = (TextView) findViewById(R.id.audioResultsTextView);
            tv1.setVisibility(1);
            tv1.setText( tv1.getText()+ " -- " + audioInfo);
        }




        return suc;
    }



}
