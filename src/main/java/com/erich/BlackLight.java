package com.erich;


import com.erich.util.Dtime;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This application will process a directory of images (assumed to be sequential frames in a video) and apply a video
 * FX algorithm to them, that I call BlackLight.
 *
 * How to get stills from a video? FFMPEG!  As simple as
 * ffmpeg -i 00061.mts  stills/output_%06d.png
 *
 * The input file is the video (*.mts, .mov, .mp4, whatever). It will write one frame per second to the stills directory
 * using the filename pattern indicated. %06d will allow numbering of 100,000 stills.
 *
 * To create video from images try this:
 * ffmpeg -start_number 1 -i new_image%d.png -vcodec mpeg4 test.mp4
 */
public class BlackLight {

    private Dtime delta = null;
    private File input;
    private File output;
    private Random rnd;

    public static void main(String[] args) throws IOException {
	// write your code here


        //get input directory OR input video - could kick off ffmpeg process from within Java.
        String input = args[0];
        //get output directory
        String output = args[1];
        //create class and run it
        BlackLight fx = new BlackLight(input, output);
        //could speed up with threads acting on a chunk of an image at a time

        fx.processStills();
    }


    public BlackLight (String input, String output)
    {
        delta = new Dtime();
        setInput(new File(input));
        setOutput(new File(output));
        rnd = new Random();
    }

    public void createStillsFromVideo(String video)
    {
        //get path and video file name

        //new way - https://github.com/brettwooldridge/NuProcess
        //call ffmpeg to write to still images - ffmpeg -i 00061.mts  stills/output_%06d.png


        //return when done
    }



    public void processStills() throws IOException
    {
        //don't use too many threads or it will NOT improve performance. 1 per CPU. Monitoring shows
        //8 uses every CPU on the mac. very nice and fast but 4 is even faster!!
        int threads = 4;

        //File[] names = input.listFiles(".png");
        File[] names = input.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".png");
            }
        });

        //names is NOT sorted on a MAC.
        Arrays.sort(names);


        //read two image files at a time (in filename order) from input directory
        //System.out.println(Arrays.toString(names));
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        int chunk = (int)names.length/threads;
        //split up names into chunks of even size, say 10. drop a final odd frame if needed.
        for (int j = 0; j < threads; j++){

            Runnable runnableTask =  new FX(j * chunk, (j + 1) * chunk, names);
            executorService.execute(runnableTask);
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
                //delta.getDeltaTime();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

    }

    public void createBlackLight(BufferedImage first, BufferedImage second, int count)
    {
        BufferedImage blImage;
        //images all same dimensions; no alpha channel expected
        // may need to examine color model. Is it RGB order?? first.getType()
        int width = first.getWidth();
        int height = first.getHeight();
        boolean hasAlphaChannel = first.getAlphaRaster() != null;
        int pixelLength = 3;
        if (hasAlphaChannel)
        {
            pixelLength = 4;
            blImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        else
        {
            pixelLength = 3;
            blImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }

        //read all data from both images into a byte array.
        byte[] frame1 = ((DataBufferByte) first.getRaster().getDataBuffer()).getData();
        byte[] frame2 = ((DataBufferByte) second.getRaster().getDataBuffer()).getData();

        //each pixel is a tuple of pixelLength. Just rip through and create a new RGBA

        int pos;

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                pos = y * width * pixelLength + x * pixelLength;
                //TODO
                //calculate new pixel color value from the other two pixels (from each image)- use getReflector

                //FIXME why losing color precision? I think I dropped to 8 bit depth?? weird.

                //bgr color order
                int p = getBlackLightColor(frame1[pos], frame1[pos + 1], frame1[pos + 2],
                        frame2[pos], frame2[pos + 1], frame2[pos + 2]);
                blImage.setRGB(x, y, p);

            }
        }


        writeImage(blImage, "new_image" + count + ".png");

        //only get blacklight a certain percentage of the time- sometimes just pass original image (maybe in streaks)
        //write the image to the output directory
        /*
        if (Math.random() > 0.09) {
            writeImage(blImage, "new_image" + count + ".png");
        }
        else
        {
            writeImage(second, "new_image" + count + ".png");
        }
        */

    }

    /*
    Create a third frame by combining pixels from the last two. This is the processed output to be used in new video.
     */
    private int getBlackLightColor(byte b1, byte g1, byte r1, byte b2, byte g2, byte r2)
    {
        int r = 0, g = 0, b= 0;
        double redScale = Math.random(), greenScale = Math.random(), blueScale = Math.random();

        /* very much mosaic/edge detect effect
        r = (byte)(r1 - r2 + Math.ceil(redScale*10));
        g = (byte)(g1 - g2 + Math.ceil(greenScale*5));
        b = (byte)(b1 - b2 + Math.ceil(blueScale*3));

        //cool. close to what processing did, but not quite
        r = Math.abs(r1 - r2);
        g = Math.abs(g1 - g2);
        b = Math.abs(b1 - b2);

        adding makes it look posterized/solarized
        r = (byte)(r1 + r2);
        g = (byte)(g1 + g2);
        b = (byte)(b1 + b2);

        //getting darker.
        r = (byte)((r1 - r2)/4);
        g = (byte)((g1 - g2)/4);
        b = (byte)((b1 - b2)/4);

        //blue
        r = 0; //(int)(r1 - r2 + Math.ceil(redScale*10))/50;
        g = 0; //(int)(g1 - g2 + Math.ceil(greenScale*5))/50;
        b = (int)(b1  + Math.ceil(blueScale*3));

        */
        //the processing code simple forces all color values back into the range of 0 to 255.
        r = reColor(r1, r2, (int)Math.ceil(redScale*10));
        g = reColor(g1, g2, (int)Math.ceil(greenScale*5));
        b = reColor(b1, b2, (int)Math.ceil(blueScale*3));

        return   r<<16 | g<<8 | b;
    }


    private int reColor(byte by1, byte by2, int scale)
    {
        //in case of extreme color change, maybe just return original color- somewhat like processing was doing
        int c = 0, b1 =0, b2 = 0;

        c = (by1 - by2) + scale;

        //keep to 8 bits
        if (c < 0) return 0;
        if (c > 255) return 255;
        return c;
    }
    /*
    color getReflector(float input, color current, last color: float r, float g, float b)
{
  float red = 0, green = 0, blue = 0;

  float redScale = (float)Math.random(), greenScale = (float)Math.random(), blueScale = (float)Math.random();

  //looks cool
  red = r - red(current) + redScale * 10;
  green = g - green(current) + greenScale * 5;
  blue = b - blue(current) + blueScale * 3;

  return color(red, green, blue);
}
     */


    public void writeImage(BufferedImage image, String name) {

        File ImageFile = new File(getOutput().toString() + File.separator + name);
        try {
            ImageIO.write(image, "png", ImageFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public File getInput() {
        return input;
    }

    public void setInput(File input) {
        this.input = input;
    }

    public File getOutput() {
        return output;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public BufferedImage readImage(File name) throws IOException
    {
        return ImageIO.read(name);
    }



    public class FX implements Runnable{

        File[] names = null;
        int start = 0;
        int stop = 0;

        public FX(int start, int stop, File[] names)
        {
            this.start = start;
            this.stop = stop;
            this.names = names;

        }


        public void run(){
            //byte[] pixels = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();
            System.out.println("started new thread; start/stop = " + start + "/" + stop);
            BufferedImage first, second;

            if (stop >= names.length -1)
            {
                stop = names.length - 2;
            }

            for (int i = start; i < stop; i++) {
                try {
                    first = readImage(names[i]);
                    second = readImage(names[i + 1]);

                    //System.out.println(names[i]);
                    //process the images
                    createBlackLight(first, second, i);
                }
                catch (IOException e) {
                    //just stop
                    e.printStackTrace();
                    System.exit(1);
                }
            }

            delta.getDeltaTime();
        }

    }
}