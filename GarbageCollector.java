public class GarbageCollector implements Runnable   {
    private Server server;

    GarbageCollector(Server serv) {
        this.server = serv;
    }

    public void run() {
        while (true)    {
            server.collectGarbage();
        }
    }
}
