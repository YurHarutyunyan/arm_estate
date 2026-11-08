package am.yerevanhomes.search;

import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

/** Remembers how far the change stream got, so a restart continues instead of starting over. */
@Component
public class SyncStateStore {

    static final String COLLECTION = "sync_state";
    private static final String ID = "listings";

    private final MongoTemplate mongo;

    public SyncStateStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public BsonDocument loadResumeToken() {
        Document state = collection().find(Filters.eq("_id", ID)).first();
        if (state == null || state.get("resumeToken") == null) {
            return null;
        }
        return state.get("resumeToken", Document.class).toBsonDocument();
    }

    public void saveResumeToken(BsonDocument token) {
        Document state = new Document("_id", ID).append("resumeToken", Document.parse(token.toJson()));
        collection().replaceOne(Filters.eq("_id", ID), state, new ReplaceOptions().upsert(true));
    }

    public void clear() {
        collection().deleteOne(Filters.eq("_id", ID));
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection(COLLECTION);
    }
}
