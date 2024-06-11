import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 3000;
    private static final String ELECTION_NAMESPACE ="/election";
    private ZooKeeper zooKeeper;
    private String currentZnodeName;

    public void connectToZookeeper() throws IOException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.None) {
            if (event.getState() == Event.KeeperState.SyncConnected) {
                System.out.println("Successfully connected to ZooKeeper");
            } else {
                synchronized (zooKeeper) {
                    zooKeeper.notifyAll();
                }
            }
        }
    }

    public void volunteerForLeadership() throws KeeperException, InterruptedException {
        String znodePrefix = ELECTION_NAMESPACE + "/c_";
        String znodeFullPath = zooKeeper.create(znodePrefix, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
//        currentNode = zooKeeper.create(znodePrefix, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Volunteered for leadership.  znode name: " + znodeFullPath);
        this.currentZnodeName = znodeFullPath.replace("/election", "");
        }

    public void electLeader() throws KeeperException, InterruptedException {
        List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
        Collections.sort(children);
        String smallestChild = children.get(0);

        if (smallestChild.equals(currentZnodeName)){
            System.out.println("I am the leader");
        }else{
            System.out.println( smallestChild + " is the leader");
            System.out.println(children);
        }
    }

    public void run() {
        try {
            volunteerForLeadership();
            electLeader();
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            LeaderElection leaderElection = new LeaderElection();
            leaderElection.connectToZookeeper();
            leaderElection.run();
            leaderElection.close();
            System.out.println("Disconnected from ZooKeeper, exiting application");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
