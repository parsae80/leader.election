package distributed.systems;

import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private static final String ELECTION_NAMESPACE = "/election/order_management";
    private ZooKeeper zooKeeper;
    private String currentZnodeName;

    public void connectToZookeeper() throws IOException, InterruptedException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
        synchronized (zooKeeper) {
            zooKeeper.wait();  // Wait for the connection to establish
        }
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.None) {
            if (event.getState() == Event.KeeperState.SyncConnected) {
                System.out.println("Successfully connected to ZooKeeper");
                synchronized (zooKeeper) {
                    zooKeeper.notify();  // Signal that connection is established
                }
            } else {
                synchronized (zooKeeper) {
                    System.out.println("Disconnected from ZooKeeper event");
                    zooKeeper.notifyAll();
                }
            }
        }
    }

    public void volunteerForLeadership() throws KeeperException, InterruptedException {
        String znodePrefix = ELECTION_NAMESPACE + "/c_";
        try {
            String znodeFullPath = zooKeeper.create(znodePrefix, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println("Volunteered for leadership. Znode name: " + znodeFullPath);
            this.currentZnodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
        } catch (KeeperException | InterruptedException e) {
            System.err.println("Failed to create znode: " + e.getMessage());
            throw e;
        }
    }

    public void electLeader() throws KeeperException, InterruptedException {
        try {
            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
            Collections.sort(children);
            String smallestChild = children.get(0);

            System.out.println("Children nodes: " + children);
            System.out.println("Current node: " + currentZnodeName);
            System.out.println("Smallest node: " + smallestChild);

            if (smallestChild.equals(currentZnodeName)) {
                System.out.println("I am the leader");
            } else {
                System.out.println("I am not the leader, " + smallestChild + " is the leader");
            }
        } catch (KeeperException | InterruptedException e) {
            System.err.println("Failed to get children nodes: " + e.getMessage());
            throw e;
        }
    }

    public void run() {
        // The run method waits for the ZooKeeper connection to be established
        synchronized (zooKeeper) {
            try {
                zooKeeper.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        try {
            LeaderElection leaderElection = new LeaderElection();
            leaderElection.connectToZookeeper();

            // Calling these methods outside the run method
            leaderElection.volunteerForLeadership();
            leaderElection.electLeader();

            leaderElection.run();
            leaderElection.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        }
    }
}
