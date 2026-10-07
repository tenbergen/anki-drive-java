//Simple Test file to have a car indefinently drive around the track
package lmedina.ACAS;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.MessageListener;
import de.adesso.anki.Vehicle;
import de.adesso.anki.messages.*;
import de.adesso.anki.messages.LightsPatternMessage.LightConfig;
import de.adesso.anki.roadmap.roadpieces.FinishRoadpiece;
import de.adesso.anki.RoadmapScanner;
import de.adesso.anki.messages.SetOffsetFromRoadCenterMessage;
import de.adesso.anki.messages.TurnMessage;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/** 
* Simple Program to make the cars move around the track indefinently
* @author Luis Medina (lmedina@oswego.edu)
* @since  09-26-2026
*/

public class Drone{

    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";


        public static void main(String[] args) throws InterruptedException {
        System.out.println("Launching connector...");
        AnkiConnector anki = null;
        try {
            anki = new AnkiConnector("192.168.235.22", 5000);
        } catch (IOException ioe) {
            System.out.println(RED + "Error connecting to server. Is it running?" + RESET);
            System.out.println("Exiting.");
            System.exit(0);
        }
        System.out.print(GREEN + "looking for cars..." + RESET);
        List<Vehicle> vehicles = anki.findVehicles();

        if (vehicles.isEmpty()) {
            System.out.println(" NO CARS FOUND. I guess that means we're done.");

        } else {
            System.out.println(" FOUND " + vehicles.size() + " CARS!");
            System.out.println(" Now connecting to and doing stuff to your cars.");

            Iterator<Vehicle> iter = vehicles.iterator();
            while (iter.hasNext()) {
                Vehicle v = iter.next();
                Drone act = new Drone(v);
                act.run();
            }
        }
        anki.close();
        System.out.println("Test complete.");
        System.exit(0);
    }

    private Vehicle v;
    private long interval = 10;

    //public TurnMessage uturn = new TurnMessage(3,1);


    public Drone(Vehicle v) {
        this.v = v;
    }

    public static void droneUpdate(Vehicle v){
        System.out.println("Vehicle Advertisement Data:");
        System.out.println("Hi it's Luis!");
        System.out.println("   " + v);
        System.out.println("      ID: " + v.getAdvertisement().getIdentifier());
        System.out.println("      Model: " + v.getAdvertisement().getModel());
        System.out.println("      Model ID: " + v.getAdvertisement().getModelId());
        System.out.println("      Product ID: " + v.getAdvertisement().getProductId());
        System.out.println("      Address: " + v.getAddress());
        System.out.println("      Color: " + v.getColor());
        System.out.println("      charging? " + v.getAdvertisement().isCharging());

    }

    public void run() throws InterruptedException {
        System.out.println("\nConnecting to " + v + " @ " + v.getAddress());
        v.connect();
        droneUpdate(v);

        // int finishLineId = FinishRoadpiece.ROADPIECE_IDS[0];


        System.out.print("   Connected. Setting SDK mode...");   //always set the SDK mode FIRST!
        v.sendMessage(new SdkModeMessage());
        System.out.println(" done.");

        System.out.print("   Sending ping...");
        //we have to set up a response handler first, in order to handle async responses
        PingResponseHandler prh = new PingResponseHandler();
        //now we tell the car, who is listening to the replies
        v.addMessageListener(PingResponseMessage.class, prh);
        //now we can actually send it.
        v.sendMessage(new PingRequestMessage());
        prh.pingSentAt = System.currentTimeMillis();
        System.out.print(" sent. Waiting at most 10secs for ping...");
        long timeout  = 10000;
        while (!prh.pingReceived && timeout > 0) {
            Thread.sleep(interval);
            timeout -= interval;
        }
        this.interval = prh.roundTrip;
        System.out.println(" Roundtrip: " + prh.roundTrip + " msec.");

        System.out.println("   Sending asynchronous Battery Level Request. Response will come eventually.");
        BatteryLevelResponseHandler blrh = new BatteryLevelResponseHandler();
        //works just like a Ping Request
        v.addMessageListener(BatteryLevelResponseMessage.class, blrh);
        v.sendMessage(new BatteryLevelRequestMessage());

        System.out.println("   Flashing lights...");
        //Lights require configurations. So first construct a lights configuration,
        //then add it to the lights pattern message, then send the message. No handler required.
        LightConfig tailLight = new LightConfig(LightsPatternMessage.LightChannel.TAIL, LightsPatternMessage.LightEffect.STROBE, 0, 0, 0);
        LightConfig Engine = new LightConfig(LightsPatternMessage.LightChannel.ENGINE_GREEN, LightsPatternMessage.LightEffect.STROBE, 160, 0, 0);

       
        LightsPatternMessage lpm = new LightsPatternMessage();
        lpm.add(tailLight);

        v.sendMessage(lpm);
        //we should sleep for at least some factor of the ping roundtrip to give the Vehicle time to set the lights
        Thread.sleep(interval * 10);

        System.out.println("   Setting Speed...");
        //Speed is easy. Just tell the car how fast to go and how quickly to accelerate.
        v.sendMessage(new SetSpeedMessage(500, 100));

        System.out.println("   Driving to Around the track...");
        //Use the sensor on the bottom to check the road pieces. This is like a response/request, but will
        //update whenever there's a new value.
    
        //Message Handlers
        // TurnMessage uturn = new TurnMessage(3,1);

        SetOffsetFromRoadCenterMessage offset = new SetOffsetFromRoadCenterMessage();
        v.sendMessage(offset);
        ChangeLaneMessage clm = new ChangeLaneMessage(90, 100, 100);

        FinishLineDetector fld = new FinishLineDetector(v);
        
        //rms.startScanning();
        // RoadmapScanner rms = new RoadmapScanner(this.v);
        // System.out.println(rms.getPieceIDs());
        v.addMessageListener(LocalizationPositionUpdateMessage.class, fld);
        v.sendMessage(new LocalizationPositionUpdateMessage());
        


        //Tryint to get the car to change lanes
        //v.sendMessage(clm);
        //v.sendMessage(uturn);
        
        int i = 0;
        
         while (!fld.stop ) {
            
            // if(fld.onFinishLine == true){
            //     v.sendMessage(uturn);
            // }
            System.out.println(i);
            //if(fld.onFinishLine == true){
            //}
            Thread.sleep(interval);
            i++;

             
         }
    
       // v.sendMessage(TurnMessage(3, 0))

        // v.sendMessage(new SetSpeedMessage(0, 12500));
        // v.disconnect();
        // System.out.println("Disconnected from " + v);
    }

    /**
     * Handles the response from the vehicle from the PingRequestMessage.
     * We need handler classes because responses from the vehicles are asynchronous.
     * Sets a received flag to true and computes the roundtrip time.
     */
    private class PingResponseHandler implements MessageListener<PingResponseMessage> {
        private boolean pingReceived = false;
        private long pingSentAt = System.currentTimeMillis();
        private long roundTrip = -1;

        @Override
        public void messageReceived(PingResponseMessage m) {
            this.pingReceived = true;
            this.roundTrip = System.currentTimeMillis() - pingSentAt;
        }
    }

    /**
     * Handles the response from the vehicle from the BatteryLevelRequestMessage.
     * Updates the battery level whenever a BatteryLEvelRequestMessage is sent.
     */
    private class BatteryLevelResponseHandler implements MessageListener<BatteryLevelResponseMessage> {
        private int batt_level;

        @Override
        public void messageReceived(BatteryLevelResponseMessage m) {
            this.batt_level = m.getBatteryLevel();
            System.out.println("   Battery Level is: " + this.batt_level + " mV");
        }
    }

    /**
     * Handles the response from the vehicle on which road piece the vehicle is
     * and sets a stop flag to true if it's the finish line.
     */
    private class FinishLineDetector implements MessageListener<LocalizationPositionUpdateMessage> {
        private int finishLineId = FinishRoadpiece.ROADPIECE_IDS[0];
        private boolean stop = false;
        public boolean onFinishLine = false;
        private Vehicle v;
        private TurnMessage uturn = new TurnMessage(3,1);

        public FinishLineDetector(Vehicle v){
             this.v = v;
        }

        @Override
        public void messageReceived(LocalizationPositionUpdateMessage m) {
            //System.out.println("Message Here");
            if (m.getRoadPieceId() == finishLineId){
                if(this.onFinishLine == false){
                System.out.println(PURPLE + "Finish Line btw"); this.onFinishLine = true; 
                v.sendMessage(uturn);
                }
            }else{ 
                if(this.onFinishLine == true){
                this.onFinishLine = false; System.out.println(CYAN + "Off the line btw"); 
                //v.sendMessage(uturn);}
            }
            }
        }

        // public void turnOnFinishLine(Vehicle v){
        //     if(this.onFinishLine == true) v.sendMessage(new TurnMessage(3, 1));

        // }
    }

}