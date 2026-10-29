package io.github.archetom.crm.domain.architecturefixture;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.lettuce.core.RedisClient;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.nio.file.Path;

/** Deliberate violations imported only by the architecture rule regression tests. */
public final class DomainFixtures {
    public static class AttachmentFileDependency {
        Path path;
    }

    public static class HttpDependency {
        HttpClient client;
    }

    public static class DatabaseDependency {
        Connection connection;
    }

    public static class RedisDependency {
        RedisClient client;
    }

    public static class MutableAggregate extends AggregateRoot<String> {
        @Override
        public String getId() {
            return "fixture";
        }

        public void setState(String state) {
        }
    }

    public static class InheritedSetterAggregate extends MutableAggregate {
    }

    public static class BusinessAggregate extends AggregateRoot<String> {
        @Override
        public String getId() {
            return "fixture";
        }

        public void changeState(String state) {
        }
    }
}
