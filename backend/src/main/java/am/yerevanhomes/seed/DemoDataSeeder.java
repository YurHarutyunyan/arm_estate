package am.yerevanhomes.seed;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import am.yerevanhomes.listing.BuildingType;
import am.yerevanhomes.listing.District;
import am.yerevanhomes.listing.Listing;
import am.yerevanhomes.user.Role;
import am.yerevanhomes.user.User;
import am.yerevanhomes.user.UserRepository;

/**
 * Fills an empty database with realistic demo listings so the live demo has something to search.
 * Uses a fixed random seed, so every environment gets the same data.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_OWNER_EMAIL = "demo-owner@yerevan-homes.am";

    /** Typical monthly rent per room in USD; central districts cost more. */
    private static final Map<District, Integer> PRICE_PER_ROOM = Map.ofEntries(
            Map.entry(District.KENTRON, 420), Map.entry(District.ARABKIR, 340), Map.entry(District.DAVTASHEN, 260),
            Map.entry(District.AJAPNYAK, 220), Map.entry(District.MALATIA_SEBASTIA, 200), Map.entry(District.SHENGAVIT, 210),
            Map.entry(District.EREBUNI, 200), Map.entry(District.NOR_NORK, 230), Map.entry(District.AVAN, 210),
            Map.entry(District.KANAKER_ZEYTUN, 240), Map.entry(District.NORK_MARASH, 280), Map.entry(District.NUBARASHEN, 170));

    private static final Map<District, List<String>> STREETS = Map.ofEntries(
            Map.entry(District.KENTRON, List.of("Tumanyan St", "Abovyan St", "Pushkin St", "Mashtots Ave", "Teryan St", "Saryan St")),
            Map.entry(District.ARABKIR, List.of("Komitas Ave", "Kievyan St", "Baghramyan Ave", "Vagharshyan St")),
            Map.entry(District.DAVTASHEN, List.of("Davtashen 2nd District", "Tigran Petrosyan St")),
            Map.entry(District.AJAPNYAK, List.of("Halabyan St", "Silikyan District", "Shinararneri St")),
            Map.entry(District.MALATIA_SEBASTIA, List.of("Sebastia St", "Raffi St", "Andranik St")),
            Map.entry(District.SHENGAVIT, List.of("Bagratunyats Ave", "Garegin Nzhdeh St", "Shirak St")),
            Map.entry(District.EREBUNI, List.of("Erebuni St", "Artsakh Ave", "Sasuntsi Davit St")),
            Map.entry(District.NOR_NORK, List.of("Gai Ave", "Mikoyan St", "Vratsakan St")),
            Map.entry(District.AVAN, List.of("Avan-Arinj District", "Khudyakov St")),
            Map.entry(District.KANAKER_ZEYTUN, List.of("Azatutyan Ave", "Ulnetsi St", "Kanaker St")),
            Map.entry(District.NORK_MARASH, List.of("Nork Marash St", "Armenakyan St")),
            Map.entry(District.NUBARASHEN, List.of("Nubarashen 1st St", "Nubarashen Highway")));

    private static final List<String> AMENITIES = List.of("balcony", "parking", "elevator", "air conditioning",
            "dishwasher", "washing machine", "heating", "wifi", "terrace", "storage room", "security", "playground");

    private static final List<String> ENGLISH_TITLES = List.of(
            "Bright %d-room apartment in %s", "Renovated %d-room flat, %s", "Cozy %d-room apartment near the metro, %s",
            "Spacious %d-room home with a view, %s", "Modern %d-room apartment in a new building, %s",
            "Quiet %d-room flat close to parks, %s", "Sunny %d-room apartment with balcony, %s");
    private static final List<String> RUSSIAN_TITLES = List.of(
            "Уютная %d-комнатная квартира, %s", "Светлая %d-комнатная квартира с ремонтом, %s",
            "Просторная %d-комнатная квартира в новостройке, %s");
    private static final List<String> ARMENIAN_TITLES = List.of(
            "%d սենյականոց բնակարան, %s", "Վերանորոգված %d սենյականոց բնակարան, %s", "Լուսավոր %d սենյականոց բնակարան, %s");

    private static final List<String> DESCRIPTIONS = List.of(
            "Freshly renovated with new windows and a modern kitchen. Close to shops, cafes and public transport.",
            "Quiet courtyard side, lots of natural light. Ten minutes on foot to the nearest metro station.",
            "Stone building with high ceilings. Ideal for a family or for working from home.",
            "Fully furnished with appliances. Utilities are not included in the rent.",
            "Great view of Mount Ararat from the living room. Supermarket and pharmacy on the ground floor.",
            "New building with an elevator and underground parking. Long-term tenants preferred.",
            "Warm in winter thanks to individual gas heating. Fast fiber internet available.");

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final int count;

    public DemoDataSeeder(MongoTemplate mongo, UserRepository users, PasswordEncoder passwordEncoder,
            @Value("${app.seed.enabled:false}") boolean enabled, @Value("${app.seed.count:300}") int count) {
        this.mongo = mongo;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.count = count;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || mongo.getCollection("listings").estimatedDocumentCount() > 0) {
            return;
        }
        String ownerId = demoOwnerId();
        Instant now = Instant.now();
        List<Document> documents = new ArrayList<>(count);
        Random random = new Random(42);
        for (int i = 0; i < count; i++) {
            Listing listing = generate(random, ownerId, now);
            Document document = new Document();
            mongo.getConverter().write(listing, document);
            documents.add(document);
        }
        // Written directly so the generated createdAt dates are kept instead of "now"
        mongo.getCollection("listings").insertMany(documents);
        log.info("Seeded {} demo listings", count);
    }

    private String demoOwnerId() {
        return users.findByEmail(DEMO_OWNER_EMAIL).map(User::getId).orElseGet(() -> {
            User owner = new User();
            owner.setEmail(DEMO_OWNER_EMAIL);
            // Random password: nobody signs in as the demo owner
            owner.setPasswordHash(passwordEncoder.encode(new ObjectId().toHexString() + new ObjectId().toHexString()));
            owner.setDisplayName("Yerevan Homes Demo");
            owner.setRoles(EnumSet.of(Role.USER, Role.OWNER));
            try {
                return users.insert(owner).getId();
            } catch (DuplicateKeyException e) {
                return users.findByEmail(DEMO_OWNER_EMAIL).orElseThrow().getId();
            }
        });
    }

    Listing generate(Random random, String ownerId, Instant now) {
        District district = District.values()[random.nextInt(District.values().length)];
        int rooms = 1 + weightedRooms(random);
        int areaSqm = 25 + rooms * (22 + random.nextInt(14));
        int totalFloors = List.of(4, 5, 9, 12, 14, 16).get(random.nextInt(6));
        int floor = 1 + random.nextInt(totalFloors);
        BuildingType buildingType = BuildingType.values()[random.nextInt(BuildingType.values().length)];
        int base = PRICE_PER_ROOM.get(district) * rooms;
        // +-25% spread, newer buildings slightly more expensive, rounded to 10
        double factor = 0.75 + random.nextDouble() * 0.5 + (buildingType == BuildingType.MONOLITH ? 0.1 : 0);
        int price = Math.max(150, (int) Math.round(base * factor / 10.0) * 10);

        Set<String> amenities = new LinkedHashSet<>();
        int amenityCount = 2 + random.nextInt(5);
        while (amenities.size() < amenityCount) {
            amenities.add(AMENITIES.get(random.nextInt(AMENITIES.size())));
        }
        if (totalFloors > 5) {
            amenities.add("elevator");
        }

        String title = title(random, rooms, district);
        String street = STREETS.get(district).get(random.nextInt(STREETS.get(district).size()));
        Instant createdAt = now.minus(Duration.ofMinutes(random.nextInt(90 * 24 * 60)));

        Listing listing = new Listing();
        listing.setId(new ObjectId().toHexString());
        listing.setVersion(0L);
        listing.setOwnerId(ownerId);
        listing.setTitle(title);
        listing.setDescription(DESCRIPTIONS.get(random.nextInt(DESCRIPTIONS.size())));
        listing.setDistrict(district);
        listing.setAddress(street + " " + (1 + random.nextInt(80)));
        listing.setPriceUsd(price);
        listing.setRooms(rooms);
        listing.setAreaSqm(areaSqm);
        listing.setFloor(floor);
        listing.setTotalFloors(totalFloors);
        listing.setBuildingType(buildingType);
        listing.setAmenities(List.copyOf(amenities));
        listing.setFurnished(random.nextInt(10) < 7);
        listing.setPetsAllowed(random.nextInt(10) < 3);
        listing.setCreatedAt(createdAt);
        listing.setUpdatedAt(createdAt);
        return listing;
    }

    /** Mostly 1-3 rooms, a few 4-5. Returns rooms minus one. */
    private static int weightedRooms(Random random) {
        int roll = random.nextInt(100);
        return roll < 25 ? 0 : roll < 60 ? 1 : roll < 85 ? 2 : roll < 96 ? 3 : 4;
    }

    private static String title(Random random, int rooms, District district) {
        int roll = random.nextInt(10);
        List<String> templates = roll < 7 ? ENGLISH_TITLES : roll < 9 ? RUSSIAN_TITLES : ARMENIAN_TITLES;
        return templates.get(random.nextInt(templates.size())).formatted(rooms, district.displayName());
    }
}
