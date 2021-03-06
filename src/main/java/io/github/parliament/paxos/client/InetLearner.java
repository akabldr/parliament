package io.github.parliament.paxos.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class InetLearner {
    private ConnectionPool connectionPool;
    private List<InetSocketAddress> peers;

    public static InetLearner create(ConnectionPool pool, List<InetSocketAddress> peers) {
        return new InetLearner(pool, peers);
    }

    private InetLearner(ConnectionPool pool, List<InetSocketAddress> peers) {
        this.connectionPool = pool;
        this.peers = peers;
    }

    public int done() {
        Optional<Integer> done = peers.stream().parallel().map(peer -> {
            SocketChannel channel = null;
            boolean failed = false;
            try {
                ClientCodec codec = new ClientCodec();
                channel = connectionPool.acquireChannel(peer);
                ByteBuffer src = codec.encodeDone();

                while (src.hasRemaining()) {
                    channel.write(src);
                }
                return codec.decodeDone(channel);
            } catch (IOException | NoConnectionInPool e) {
                failed = true;
                return -1;
            } finally {
                if (channel != null) {
                    connectionPool.releaseChannel(peer, channel, failed);
                }
            }
        }).min(Integer::compare);

        return done.orElse(-1);
    }

    public Optional<byte[]> learn(int id) {
        Optional<Optional<byte[]>> instance = peers.stream().map(peer -> {
            SocketChannel channel = null;
            boolean failed = false;
            try {
                ClientCodec codec = new ClientCodec();
                channel = connectionPool.acquireChannel(peer);
                ByteBuffer src = codec.encodeInstance(id);

                while (src.hasRemaining()) {
                    channel.write(src);
                }
                return codec.decodeInstance(id, channel);
            } catch (IOException | NoConnectionInPool e) {
                failed = true;
                return null;
            } finally {
                if (channel != null) {
                    connectionPool.releaseChannel(peer, channel, failed);
                }
            }
        }).filter(Objects::nonNull).findFirst();

        return instance.orElse(Optional.empty());
    }
}
