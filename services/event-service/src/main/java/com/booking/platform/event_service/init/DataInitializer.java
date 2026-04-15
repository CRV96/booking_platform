package com.booking.platform.event_service.init;

import com.booking.platform.common.grpc.event.CreateEventRequest;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.common.grpc.event.VenueInfo;
import com.booking.platform.event_service.document.EventDocument;
import com.booking.platform.event_service.document.enums.EventStatus;
import com.booking.platform.event_service.dto.OrganizerDto;
import com.booking.platform.event_service.repository.EventRepository;
import com.booking.platform.event_service.service.EventService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seeds the MongoDB database with sample events on startup.
 *
 * <p>Active only in the {@code dev} profile. Idempotent — if events already exist
 * in the database the initializer exits immediately, so restarting the service
 * does not create duplicates.
 *
 * <p>Creates 50 events spread across all six categories, all four statuses, and
 * multiple years to support realistic browsing, searching, and filtering demos:
 * <ul>
 *   <li>28 PUBLISHED events — near/far future, visible to all users</li>
 *   <li>12 COMPLETED events — past dates (2022-2024), useful for history views</li>
 *   <li>6  DRAFT events — future dates, visible only to employees (publish flow demo)</li>
 *   <li>4  CANCELLED events — demonstrate the cancelled state in the UI</li>
 * </ul>
 *
 * <p>PUBLISHED, DRAFT, and CANCELLED events go through the full service pipeline
 * ({@link EventService#createEvent}, {@link EventService#publishEvent},
 * {@link EventService#cancelEvent}) so they pass validation and populate caches.
 * COMPLETED events are saved directly via the repository because there is no
 * {@code completeEvent()} service method — the status is set after publishing
 * and the {@code dateTime} is forced into the past.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final EventService eventService;
    private final EventRepository eventRepository;

    // ── Organizers ────────────────────────────────────────────────────────────

    private static final OrganizerDto ADMIN = OrganizerDto.builder()
            .userId("admin")
            .name("Admin User")
            .email("admin@booking.platform")
            .build();

    private static final OrganizerDto JANE = OrganizerDto.builder()
            .userId("jane")
            .name("Jane Organizer")
            .email("jane@booking.platform")
            .build();

    private static final OrganizerDto CARLOS = OrganizerDto.builder()
            .userId("carlos")
            .name("Carlos Events")
            .email("carlos@booking.platform")
            .build();

    // =========================================================================
    // Entry point
    // =========================================================================

    @Override
    public void run(ApplicationArguments args) {
        if (eventRepository.count() > 0) {
            ApplicationLogger.logMessage(log, Level.INFO, "DataInitializer: events already exist — skipping seed (found {} events)",
                    eventRepository.count());
            return;
        }

        ApplicationLogger.logMessage(log, Level.INFO, "DataInitializer: seeding 50 sample events...");

        seedConcerts();
        seedSportsEvents();
        seedTheatreEvents();
        seedConferences();
        seedFestivals();
        seedOtherEvents();

        long total = eventRepository.count();
        ApplicationLogger.logMessage(log, Level.INFO, "DataInitializer: seeding complete — {} events created", total);
    }

    // =========================================================================
    // CONCERTS — 10 events: 5 published, 3 completed, 1 draft, 1 cancelled
    // =========================================================================

    private void seedConcerts() {

        // ── PUBLISHED (near future) ───────────────────────────────────────────

        createAndPublish(
                "Coldplay: Music of the Spheres World Tour",
                "One of the biggest stadium tours of the decade. An unforgettable night of " +
                "hits spanning Coldplay's entire career, with a dazzling light show.",
                "CONCERT",
                future(30), future(30),
                venue("Wembley Stadium", "Stadium Way", "London", "UK", 90000),
                List.of(
                        seat("Floor", 250.00, "GBP", 5000),
                        seat("Lower Tier", 120.00, "GBP", 25000),
                        seat("Upper Tier", 65.00, "GBP", 60000)
                ),
                List.of("coldplay", "pop", "rock", "stadium-tour"),
                ADMIN
        );

        createAndPublish(
                "Taylor Swift: The Eras Tour — London Night",
                "A once-in-a-generation concert experience celebrating every era of Taylor Swift's " +
                "record-breaking career. Over three hours of non-stop hits.",
                "CONCERT",
                future(45), future(45),
                venue("Tottenham Hotspur Stadium", "782 High Rd", "London", "UK", 62850),
                List.of(
                        seat("Floor GA", 350.00, "GBP", 10000),
                        seat("Lower Tier", 200.00, "GBP", 25000),
                        seat("Upper Tier", 95.00, "GBP", 27850)
                ),
                List.of("taylor-swift", "pop", "eras-tour"),
                JANE
        );

        createAndPublish(
                "Depeche Mode: Memento Mori Tour — Paris",
                "The legendary electronic band returns to the road in support of their " +
                "critically acclaimed album. A darkly beautiful spectacle.",
                "CONCERT",
                future(60), future(60),
                venue("AccorHotels Arena", "8 Bd de Bercy", "Paris", "France", 20300),
                List.of(
                        seat("Floor", 180.00, "EUR", 4000),
                        seat("Category 1", 120.00, "EUR", 8000),
                        seat("Category 2", 75.00, "EUR", 8300)
                ),
                List.of("depeche-mode", "electronic", "synth-pop"),
                CARLOS
        );

        // ── PUBLISHED (far future 2026) ────────────────────────────────────────

        createAndPublish(
                "Daft Punk Reunion: One More Time Tour 2026",
                "After years of speculation, the robots are back. An iconic electronic spectacle " +
                "with pyramid staging and a career-spanning set.",
                "CONCERT",
                future(400), future(400),
                venue("Stade de France", "93216 Saint-Denis", "Paris", "France", 80000),
                List.of(
                        seat("Floor", 220.00, "EUR", 15000),
                        seat("Category 1", 150.00, "EUR", 35000),
                        seat("Category 2", 90.00, "EUR", 30000)
                ),
                List.of("daft-punk", "electronic", "reunion", "2026"),
                JANE
        );

        createAndPublish(
                "Beyoncé: Renaissance World Tour 2026",
                "The Renaissance tour returns for a second global run, celebrating the album's " +
                "anniversary with an expanded production and new visuals.",
                "CONCERT",
                future(500), future(500),
                venue("Mercedes-Benz Stadium", "1 AMB Dr NW", "Atlanta", "USA", 71000),
                List.of(
                        seat("Floor GA", 400.00, "USD", 12000),
                        seat("100 Level", 220.00, "USD", 28000),
                        seat("200 Level", 110.00, "USD", 31000)
                ),
                List.of("beyonce", "pop", "rnb", "renaissance"),
                ADMIN
        );

        // ── DRAFT ─────────────────────────────────────────────────────────────

        createDraft(
                "Radiohead Reunion: The Bends 30th Anniversary",
                "An exclusive arena show celebrating 30 years of The Bends. Radiohead performs " +
                "the seminal album in full alongside a career-spanning set.",
                "CONCERT",
                future(90), future(90),
                venue("O2 Arena", "Peninsula Square", "London", "UK", 20000),
                List.of(
                        seat("Floor", 200.00, "GBP", 3000),
                        seat("Level 1", 130.00, "GBP", 8000),
                        seat("Level 2", 80.00, "GBP", 9000)
                ),
                List.of("radiohead", "alternative", "rock"),
                ADMIN
        );

        // ── CANCELLED ─────────────────────────────────────────────────────────

        createAndCancel(
                "New Year's Eve Countdown Concert 2025",
                "A spectacular outdoor countdown concert with live acts and fireworks. " +
                "Unfortunately cancelled due to adverse weather forecasts.",
                "CONCERT",
                future(15), future(15),
                venue("Piata Constitutiei", "Piata Constitutiei", "Bucharest", "Romania", 50000),
                List.of(
                        seat("General Admission", 30.00, "RON", 50000)
                ),
                List.of("new-year", "countdown", "outdoor"),
                CARLOS
        );

        // ── COMPLETED (past events) ────────────────────────────────────────────

        createCompleted(
                "Ed Sheeran: Mathematics Tour — Dublin",
                "Ed Sheeran's record-breaking Mathematics Tour live at Croke Park. " +
                "A solo stadium show like no other.",
                "CONCERT",
                past(200), past(200),
                venue("Croke Park", "Jones' Road", "Dublin", "Ireland", 82300),
                List.of(
                        seat("Floor GA", 130.00, "EUR", 15000),
                        seat("Lower Tier", 95.00, "EUR", 40000),
                        seat("Upper Tier", 60.00, "EUR", 27300)
                ),
                List.of("ed-sheeran", "pop", "mathematics-tour"),
                JANE
        );

        createCompleted(
                "Elton John: Farewell Yellow Brick Road — Berlin",
                "The final night of Sir Elton John's legendary farewell tour in Berlin. " +
                "A career retrospective spanning six decades.",
                "CONCERT",
                past(400), past(400),
                venue("Olympiastadion", "Olympischer Platz 3", "Berlin", "Germany", 74244),
                List.of(
                        seat("Floor", 200.00, "EUR", 8000),
                        seat("Category 1", 140.00, "EUR", 30000),
                        seat("Category 2", 85.00, "EUR", 36244)
                ),
                List.of("elton-john", "rock", "farewell-tour"),
                ADMIN
        );

        createCompleted(
                "Arctic Monkeys: The Car Tour — Barcelona",
                "Alex Turner and the band play their critically acclaimed album The Car at " +
                "Palau Sant Jordi. An intimate yet expansive production.",
                "CONCERT",
                past(600), past(600),
                venue("Palau Sant Jordi", "Passeig Olímpic 5-7", "Barcelona", "Spain", 17000),
                List.of(
                        seat("Floor", 90.00, "EUR", 5000),
                        seat("Category 1", 70.00, "EUR", 7000),
                        seat("Category 2", 50.00, "EUR", 5000)
                ),
                List.of("arctic-monkeys", "rock", "indie"),
                CARLOS
        );
    }

    // =========================================================================
    // SPORTS — 9 events: 4 published, 2 completed, 2 draft, 1 cancelled
    // =========================================================================

    private void seedSportsEvents() {

        // ── PUBLISHED ─────────────────────────────────────────────────────────

        createAndPublish(
                "UEFA Champions League Final 2025",
                "The biggest club football match in the world. Two of Europe's elite clubs " +
                "compete for the most coveted trophy in club football.",
                "SPORTS",
                future(20), future(20),
                venue("Allianz Arena", "Werner-Heisenberg-Allee 25", "Munich", "Germany", 75000),
                List.of(
                        seat("Category 1", 500.00, "EUR", 10000),
                        seat("Category 2", 300.00, "EUR", 30000),
                        seat("Category 3", 150.00, "EUR", 35000)
                ),
                List.of("football", "uefa", "champions-league"),
                ADMIN
        );

        createAndPublish(
                "Wimbledon Men's Finals Day 2025",
                "Centre Court experience for the most prestigious tennis final in the world. " +
                "Tradition, elegance, and world-class tennis at the All England Club.",
                "SPORTS",
                future(55), future(55),
                venue("All England Club", "Church Rd", "London", "UK", 15000),
                List.of(
                        seat("Centre Court Debenture", 1200.00, "GBP", 500),
                        seat("Centre Court", 250.00, "GBP", 4000),
                        seat("Court 1", 80.00, "GBP", 10500)
                ),
                List.of("tennis", "wimbledon", "grand-slam"),
                JANE
        );

        createAndPublish(
                "NBA Finals 2026 — Game 7",
                "The ultimate winner-take-all showdown in professional basketball. " +
                "Witness history being made under the bright lights of the NBA Finals.",
                "SPORTS",
                future(420), future(420),
                venue("Chase Center", "1 Warriors Way", "San Francisco", "USA", 18064),
                List.of(
                        seat("Courtside", 5000.00, "USD", 200),
                        seat("Lower Bowl", 800.00, "USD", 8000),
                        seat("Upper Bowl", 250.00, "USD", 9864)
                ),
                List.of("nba", "basketball", "finals", "2026"),
                CARLOS
        );

        createAndPublish(
                "Tour de France — Paris Final Stage 2026",
                "Witness the thrilling conclusion of cycling's greatest race on the " +
                "Champs-Élysées. Watch the champions sprint for glory.",
                "SPORTS",
                future(750), future(750),
                venue("Champs-Élysées", "Avenue des Champs-Élysées", "Paris", "France", 300000),
                List.of(
                        seat("VIP Tribune", 350.00, "EUR", 2000),
                        seat("Grandstand", 120.00, "EUR", 8000),
                        seat("Street View (Free)", 0.00, "EUR", 290000)
                ),
                List.of("cycling", "tour-de-france", "paris", "2026"),
                ADMIN
        );

        // ── DRAFT ─────────────────────────────────────────────────────────────

        createDraft(
                "Formula 1 Monaco Grand Prix 2025",
                "The jewel of the Formula 1 calendar. Experience the most glamorous and technically " +
                "demanding race through the streets of Monte-Carlo.",
                "SPORTS",
                future(75), future(75),
                venue("Circuit de Monaco", "Route de la Piscine", "Monte Carlo", "Monaco", 37000),
                List.of(
                        seat("K1 Tribune", 900.00, "EUR", 2000),
                        seat("T Tribune", 600.00, "EUR", 10000),
                        seat("General Admission", 150.00, "EUR", 25000)
                ),
                List.of("formula1", "motorsport", "monaco"),
                CARLOS
        );

        createDraft(
                "Rugby World Cup Final 2027",
                "The pinnacle of international rugby. Two nations battle for the Webb Ellis Cup " +
                "in front of a sold-out stadium.",
                "SPORTS",
                future(800), future(800),
                venue("Stade de France", "93216 Saint-Denis", "Paris", "France", 80000),
                List.of(
                        seat("Category 1", 450.00, "EUR", 20000),
                        seat("Category 2", 250.00, "EUR", 35000),
                        seat("Category 3", 120.00, "EUR", 25000)
                ),
                List.of("rugby", "world-cup", "2027"),
                JANE
        );

        // ── CANCELLED ─────────────────────────────────────────────────────────

        createAndCancel(
                "City Marathon 2025 — Spring Edition",
                "The annual city marathon attracting 40,000 runners from around the world. " +
                "Cancelled this edition due to road infrastructure works.",
                "SPORTS",
                future(25), future(25),
                venue("City Centre", "Marathon Start Line", "Bucharest", "Romania", 40000),
                List.of(
                        seat("Elite Runner", 80.00, "RON", 500),
                        seat("Standard Entry", 60.00, "RON", 39500)
                ),
                List.of("marathon", "running", "bucharest"),
                ADMIN
        );

        // ── COMPLETED ─────────────────────────────────────────────────────────

        createCompleted(
                "FIFA World Cup Final 2022 — Qatar",
                "France vs Argentina in the most dramatic World Cup final in history. " +
                "Mbappe's hat-trick and Messi's golden moment at Lusail Stadium.",
                "SPORTS",
                past(850), past(850),
                venue("Lusail Stadium", "Lusail", "Lusail", "Qatar", 88966),
                List.of(
                        seat("Category 1", 1500.00, "USD", 20000),
                        seat("Category 2", 800.00, "USD", 40000),
                        seat("Category 3", 400.00, "USD", 28966)
                ),
                List.of("football", "world-cup", "fifa", "2022"),
                JANE
        );

        createCompleted(
                "Wimbledon Men's Finals Day 2023",
                "Carlos Alcaraz vs Novak Djokovic — a five-set classic that had Centre Court " +
                "on its feet for nearly five hours.",
                "SPORTS",
                past(580), past(580),
                venue("All England Club", "Church Rd", "London", "UK", 15000),
                List.of(
                        seat("Centre Court Debenture", 1100.00, "GBP", 500),
                        seat("Centre Court", 230.00, "GBP", 4000),
                        seat("Court 1", 75.00, "GBP", 10500)
                ),
                List.of("tennis", "wimbledon", "grand-slam", "2023"),
                ADMIN
        );
    }

    // =========================================================================
    // THEATRE — 8 events: 4 published, 2 completed, 1 draft, 1 cancelled
    // =========================================================================

    private void seedTheatreEvents() {

        // ── PUBLISHED ─────────────────────────────────────────────────────────

        createAndPublish(
                "Hamilton — Original West End Cast Returns",
                "The hip-hop musical phenomenon returns to the West End with its original cast " +
                "for a strictly limited six-week run.",
                "THEATRE",
                future(14), future(58),
                venue("Victoria Palace Theatre", "Victoria Street", "London", "UK", 1550),
                List.of(
                        seat("Stalls Premium", 220.00, "GBP", 300),
                        seat("Stalls", 150.00, "GBP", 600),
                        seat("Dress Circle", 90.00, "GBP", 650)
                ),
                List.of("musical", "hamilton", "west-end"),
                CARLOS
        );

        createAndPublish(
                "The Phantom of the Opera — Farewell Tour",
                "Andrew Lloyd Webber's iconic musical embarks on its final world tour. " +
                "Do not miss the Phantom's last haunting of the Paris Opéra House.",
                "THEATRE",
                future(22), future(26),
                venue("Palais Garnier", "Place de l'Opéra", "Paris", "France", 1979),
                List.of(
                        seat("Orchestra Premium", 280.00, "EUR", 200),
                        seat("Orchestra", 160.00, "EUR", 800),
                        seat("Balcony", 75.00, "EUR", 979)
                ),
                List.of("musical", "phantom", "opera"),
                JANE
        );

        createAndPublish(
                "Shakespeare's Hamlet — RSC Production",
                "The Royal Shakespeare Company presents a modern interpretation of the greatest " +
                "tragedy ever written, starring a BAFTA-winning lead.",
                "THEATRE",
                future(18), future(40),
                venue("RSC Theatre", "Waterside", "Stratford-upon-Avon", "UK", 1040),
                List.of(
                        seat("Premium Stalls", 95.00, "GBP", 200),
                        seat("Stalls", 65.00, "GBP", 440),
                        seat("Circle", 45.00, "GBP", 400)
                ),
                List.of("shakespeare", "rsc", "drama", "hamlet"),
                ADMIN
        );

        createAndPublish(
                "Chicago — 50th Anniversary Production",
                "Razzle dazzle them! Broadway's longest-running American musical celebrates " +
                "its 50th anniversary with a lavish new production.",
                "THEATRE",
                future(380), future(420),
                venue("Ambassador Theatre", "219 W 49th St", "New York", "USA", 1125),
                List.of(
                        seat("Orchestra Premium", 300.00, "USD", 200),
                        seat("Orchestra", 185.00, "USD", 600),
                        seat("Mezzanine", 110.00, "USD", 325)
                ),
                List.of("musical", "chicago", "broadway", "2026"),
                CARLOS
        );

        // ── DRAFT ─────────────────────────────────────────────────────────────

        createDraft(
                "Les Misérables — Arena Spectacular",
                "The beloved musical reimagined for arenas, with a full orchestra and " +
                "grand-scale staging. Coming to European cities in 2026.",
                "THEATRE",
                future(500), future(505),
                venue("The O2 Arena", "Peninsula Square", "London", "UK", 20000),
                List.of(
                        seat("Floor Premium", 180.00, "GBP", 2000),
                        seat("Category 1", 120.00, "GBP", 10000),
                        seat("Category 2", 75.00, "GBP", 8000)
                ),
                List.of("musical", "les-miserables", "arena", "2026"),
                JANE
        );

        // ── CANCELLED ─────────────────────────────────────────────────────────

        createAndCancel(
                "Macbeth — Touring Production 2025",
                "A site-specific production of Macbeth performed in historic castles across Scotland. " +
                "Cancelled due to venue restoration works.",
                "THEATRE",
                future(50), future(65),
                venue("Edinburgh Castle", "Castlehill", "Edinburgh", "UK", 500),
                List.of(
                        seat("General Admission", 75.00, "GBP", 500)
                ),
                List.of("shakespeare", "macbeth", "scotland"),
                ADMIN
        );

        // ── COMPLETED ─────────────────────────────────────────────────────────

        createCompleted(
                "Wicked — Broadway 30th Anniversary",
                "The global smash hit celebrating three decades of defying gravity. " +
                "A special limited engagement with the original Broadway leads.",
                "THEATRE",
                past(300), past(295),
                venue("Gershwin Theatre", "222 W 51st St", "New York", "USA", 1933),
                List.of(
                        seat("Orchestra Premium", 280.00, "USD", 300),
                        seat("Orchestra", 175.00, "USD", 900),
                        seat("Mezzanine", 115.00, "USD", 733)
                ),
                List.of("musical", "wicked", "broadway"),
                CARLOS
        );

        createCompleted(
                "King Lear — National Theatre Live 2023",
                "The National Theatre's celebrated production of King Lear, featuring an " +
                "Olivier Award-winning performance in the title role.",
                "THEATRE",
                past(500), past(498),
                venue("National Theatre", "South Bank", "London", "UK", 1150),
                List.of(
                        seat("Premium Stalls", 85.00, "GBP", 200),
                        seat("Stalls", 60.00, "GBP", 550),
                        seat("Circle", 40.00, "GBP", 400)
                ),
                List.of("shakespeare", "national-theatre", "drama"),
                JANE
        );
    }

    // =========================================================================
    // CONFERENCES — 8 events: 4 published, 2 completed, 1 draft, 1 cancelled
    // =========================================================================

    private void seedConferences() {

        // ── PUBLISHED ─────────────────────────────────────────────────────────

        createAndPublish(
                "AWS re:Invent 2025",
                "Amazon Web Services' annual cloud computing conference. Five days of keynotes, " +
                "technical sessions, workshops, and certification exams.",
                "CONFERENCE",
                future(120), future(125),
                venue("The Venetian Expo", "3355 Las Vegas Blvd S", "Las Vegas", "USA", 50000),
                List.of(
                        seat("Full Pass", 2100.00, "USD", 30000),
                        seat("Builder Pass", 1200.00, "USD", 15000),
                        seat("Digital Pass", 299.00, "USD", 5000)
                ),
                List.of("aws", "cloud", "tech", "devops"),
                ADMIN
        );

        createAndPublish(
                "Web Summit 2025",
                "Europe's largest technology conference returns to Lisbon. Thousands of startups, " +
                "investors, and industry leaders gather to shape the future of technology.",
                "CONFERENCE",
                future(95), future(99),
                venue("Altice Arena", "Rossio dos Olivais", "Lisbon", "Portugal", 20000),
                List.of(
                        seat("Startup Pass", 595.00, "EUR", 10000),
                        seat("Investor Pass", 2500.00, "EUR", 2000),
                        seat("General Admission", 1200.00, "EUR", 8000)
                ),
                List.of("tech", "startup", "web-summit", "innovation"),
                JANE
        );

        createAndPublish(
                "Google I/O Extended Tokyo 2025",
                "The official Google I/O extended event in Tokyo. Live streams of all keynotes, " +
                "local sessions on Android, Flutter, AI, and Google Cloud.",
                "CONFERENCE",
                future(40), future(42),
                venue("Google Japan Office", "Roppongi Hills Mori Tower", "Tokyo", "Japan", 1200),
                List.of(
                        seat("Developer Pass", 0.00, "JPY", 800),
                        seat("Workshop Add-on", 5000.00, "JPY", 400)
                ),
                List.of("google", "io", "android", "flutter", "ai"),
                CARLOS
        );

        createAndPublish(
                "VivaTech Paris 2026",
                "Europe's biggest startup and tech event returns to Paris Nord Villepinte. " +
                "Four days of innovation, investment, and inspiration.",
                "CONFERENCE",
                future(460), future(464),
                venue("Paris Nord Villepinte", "ZAC Paris Nord 2", "Paris", "France", 45000),
                List.of(
                        seat("Startup Pass", 700.00, "EUR", 20000),
                        seat("Investor Pass", 3000.00, "EUR", 3000),
                        seat("Visitor Pass", 300.00, "EUR", 22000)
                ),
                List.of("tech", "startup", "vivatech", "paris", "2026"),
                ADMIN
        );

        // ── DRAFT ─────────────────────────────────────────────────────────────

        createDraft(
                "JavaOne 2026 — San Francisco",
                "Oracle's flagship Java developer conference returns after a multi-year hiatus. " +
                "Keynotes, deep dives, and hands-on labs for the Java ecosystem.",
                "CONFERENCE",
                future(560), future(563),
                venue("Moscone Center", "747 Howard St", "San Francisco", "USA", 15000),
                List.of(
                        seat("Full Conference Pass", 1800.00, "USD", 8000),
                        seat("One-Day Pass", 600.00, "USD", 7000)
                ),
                List.of("java", "jvm", "oracle", "developer", "2026"),
                CARLOS
        );

        // ── CANCELLED ─────────────────────────────────────────────────────────

        createAndCancel(
                "BlockWorld Crypto Summit 2025",
                "An international conference on blockchain, DeFi, and Web3 technologies. " +
                "Cancelled due to sponsor withdrawal.",
                "CONFERENCE",
                future(70), future(71),
                venue("ExCeL London", "One Western Gateway", "London", "UK", 5000),
                List.of(
                        seat("Full Pass", 999.00, "USD", 3000),
                        seat("Day Pass", 350.00, "USD", 2000)
                ),
                List.of("blockchain", "crypto", "web3", "defi"),
                JANE
        );

        // ── COMPLETED ─────────────────────────────────────────────────────────

        createCompleted(
                "AWS re:Invent 2023",
                "Amazon Web Services' 2023 annual conference with keynotes on generative AI, " +
                "serverless, and the next generation of cloud infrastructure.",
                "CONFERENCE",
                past(450), past(445),
                venue("The Venetian Expo", "3355 Las Vegas Blvd S", "Las Vegas", "USA", 50000),
                List.of(
                        seat("Full Pass", 1800.00, "USD", 30000),
                        seat("Builder Pass", 1100.00, "USD", 15000),
                        seat("Digital Pass", 249.00, "USD", 5000)
                ),
                List.of("aws", "cloud", "tech", "genai", "2023"),
                ADMIN
        );

        createCompleted(
                "Web Summit 2022 — Lisbon",
                "The 2022 edition of Web Summit welcomed 70,000 attendees from 160 countries " +
                "to explore the themes of AI, climate tech, and the future of work.",
                "CONFERENCE",
                past(800), past(796),
                venue("Altice Arena", "Rossio dos Olivais", "Lisbon", "Portugal", 20000),
                List.of(
                        seat("Startup Pass", 495.00, "EUR", 10000),
                        seat("Investor Pass", 2000.00, "EUR", 2000),
                        seat("General Admission", 995.00, "EUR", 8000)
                ),
                List.of("tech", "startup", "web-summit", "2022"),
                JANE
        );
    }

    // =========================================================================
    // FESTIVALS — 9 events: 5 published, 2 completed, 1 draft, 1 cancelled
    // =========================================================================

    private void seedFestivals() {

        // ── PUBLISHED ─────────────────────────────────────────────────────────

        createAndPublish(
                "Glastonbury Festival 2025",
                "The world's most famous music and performing arts festival returns to Worthy Farm. " +
                "Five days of music across hundreds of stages.",
                "FESTIVAL",
                future(50), future(55),
                venue("Worthy Farm", "Pilton", "Glastonbury", "UK", 210000),
                List.of(
                        seat("Weekend Ticket", 350.00, "GBP", 135000),
                        seat("Weekend + Camping", 400.00, "GBP", 75000)
                ),
                List.of("glastonbury", "music", "festival", "camping"),
                CARLOS
        );

        createAndPublish(
                "Coachella Valley Music and Arts Festival 2025",
                "The desert music and arts festival that defines the beginning of festival season. " +
                "Three weekends of iconic headliners, art installations, and fashion.",
                "FESTIVAL",
                future(35), future(37),
                venue("Empire Polo Club", "81800 Ave 51", "Indio", "USA", 125000),
                List.of(
                        seat("GA Weekend Pass", 549.00, "USD", 80000),
                        seat("VIP Weekend Pass", 1099.00, "USD", 20000),
                        seat("Car Camping Add-on", 150.00, "USD", 25000)
                ),
                List.of("coachella", "music", "festival", "arts"),
                ADMIN
        );

        createAndPublish(
                "Oktoberfest München 2025",
                "The world's largest folk festival returns to the Theresienwiese meadows. " +
                "Sixteen days of traditional Bavarian culture, beer, music, and fairground rides.",
                "FESTIVAL",
                future(65), future(82),
                venue("Theresienwiese", "Theresienwiese", "Munich", "Germany", 400000),
                List.of(
                        seat("Tent Reservation", 50.00, "EUR", 200000),
                        seat("VIP Tent Package", 200.00, "EUR", 50000),
                        seat("Free Entry", 0.00, "EUR", 150000)
                ),
                List.of("oktoberfest", "festival", "bavarian", "beer"),
                JANE
        );

        createAndPublish(
                "Tomorrowland 2026 — Belgium",
                "The world's greatest electronic music festival returns for another magical " +
                "edition at the legendary De Schorre domain in Boom, Belgium.",
                "FESTIVAL",
                future(430), future(435),
                venue("De Schorre", "Boom", "Antwerp", "Belgium", 200000),
                List.of(
                        seat("Full Madness Weekend Pass", 280.00, "EUR", 100000),
                        seat("DreamVille Camping", 350.00, "EUR", 70000),
                        seat("1-Day Pass", 100.00, "EUR", 30000)
                ),
                List.of("tomorrowland", "edm", "electronic", "festival", "2026"),
                CARLOS
        );

        createAndPublish(
                "Reading Festival 2025",
                "One of the UK's most iconic music festivals, bringing rock, indie, and electronic " +
                "acts to Reading over the August bank holiday weekend.",
                "FESTIVAL",
                future(110), future(113),
                venue("Little John's Farm", "Reading", "Berkshire", "UK", 105000),
                List.of(
                        seat("Weekend + Camping", 280.00, "GBP", 80000),
                        seat("Weekend No Camping", 230.00, "GBP", 25000)
                ),
                List.of("reading-festival", "rock", "indie", "uk"),
                ADMIN
        );

        // ── DRAFT ─────────────────────────────────────────────────────────────

        createDraft(
                "Burning Man 2026",
                "The annual experiment in radical self-expression returns to Black Rock City, Nevada. " +
                "A city built and burned in one week.",
                "FESTIVAL",
                future(650), future(659),
                venue("Black Rock City", "Black Rock Desert", "Nevada", "USA", 80000),
                List.of(
                        seat("Main Event Ticket", 575.00, "USD", 70000),
                        seat("Vehicle Pass", 150.00, "USD", 30000)
                ),
                List.of("burning-man", "festival", "art", "nevada", "2026"),
                JANE
        );

        // ── CANCELLED ─────────────────────────────────────────────────────────

        createAndCancel(
                "Woodstock 2025 Revival",
                "A new festival celebrating the spirit of the original 1969 Woodstock. " +
                "Cancelled after permitting issues with local authorities.",
                "FESTIVAL",
                future(130), future(133),
                venue("Bethel Woods", "200 Hurd Road", "Bethel", "USA", 50000),
                List.of(
                        seat("General Admission", 199.00, "USD", 40000),
                        seat("VIP", 499.00, "USD", 10000)
                ),
                List.of("woodstock", "rock", "festival"),
                CARLOS
        );

        // ── COMPLETED ─────────────────────────────────────────────────────────

        createCompleted(
                "Glastonbury Festival 2023",
                "The 2023 edition headlined by Arctic Monkeys, Guns N' Roses and Elton John's " +
                "final Glastonbury performance. A historic year on the Pyramid Stage.",
                "FESTIVAL",
                past(600), past(595),
                venue("Worthy Farm", "Pilton", "Glastonbury", "UK", 210000),
                List.of(
                        seat("Weekend Ticket", 335.00, "GBP", 135000),
                        seat("Weekend + Camping", 385.00, "GBP", 75000)
                ),
                List.of("glastonbury", "music", "festival", "2023"),
                JANE
        );

        createCompleted(
                "Coachella 2022",
                "The first Coachella since the pandemic, headlined by The Weeknd, Billie Eilish, " +
                "and a legendary Swedish House Mafia & The Weeknd closing set.",
                "FESTIVAL",
                past(950), past(947),
                venue("Empire Polo Club", "81800 Ave 51", "Indio", "USA", 125000),
                List.of(
                        seat("GA Weekend Pass", 499.00, "USD", 80000),
                        seat("VIP Weekend Pass", 999.00, "USD", 20000)
                ),
                List.of("coachella", "music", "festival", "2022"),
                ADMIN
        );
    }

    // =========================================================================
    // OTHER — 6 events: 6 published, 1 cancelled  (total: 5 pub + 1 cancelled)
    // =========================================================================

    private void seedOtherEvents() {

        // ── PUBLISHED ─────────────────────────────────────────────────────────

        createAndPublish(
                "Bucharest International Film Festival 2025",
                "Ten days celebrating the best in independent and arthouse cinema from around the world. " +
                "Screenings, Q&As with directors, and an awards ceremony at the historic cinema.",
                "OTHER",
                future(25), future(35),
                venue("Cinema Pro", "Str. Ion Ghica 3", "Bucharest", "Romania", 300),
                List.of(
                        seat("Full Festival Pass", 150.00, "RON", 100),
                        seat("Single Screening", 25.00, "RON", 200)
                ),
                List.of("film", "cinema", "bucharest", "independent"),
                CARLOS
        );

        createAndPublish(
                "TEDxBucharest 2025",
                "Ideas worth spreading — TEDxBucharest returns with 12 speakers tackling the " +
                "biggest questions in science, design, and social change.",
                "OTHER",
                future(80), future(80),
                venue("Ateneul Român", "Strada Benjamin Franklin 1", "Bucharest", "Romania", 800),
                List.of(
                        seat("Standard Seat", 200.00, "RON", 600),
                        seat("Premium Seat", 400.00, "RON", 200)
                ),
                List.of("tedx", "talks", "innovation", "bucharest"),
                ADMIN
        );

        createAndPublish(
                "Street Food Festival — Summer Edition 2025",
                "The biggest street food festival in Eastern Europe brings together 150 vendors " +
                "from 40 countries for a week of flavour and culture.",
                "OTHER",
                future(48), future(53),
                venue("Herăstrău Park", "Soseaua Nordului", "Bucharest", "Romania", 30000),
                List.of(
                        seat("Day Ticket", 30.00, "RON", 20000),
                        seat("Week Pass", 100.00, "RON", 10000)
                ),
                List.of("food", "street-food", "festival", "summer"),
                JANE
        );

        createAndPublish(
                "Japan National Cherry Blossom Festival 2026",
                "Welcome spring with hanami — viewing the famous sakura blossoms in Ueno Park " +
                "accompanied by traditional music, food stalls, and lantern displays.",
                "OTHER",
                future(440), future(445),
                venue("Ueno Park", "Ueno Koen", "Tokyo", "Japan", 50000),
                List.of(
                        seat("Free Entry (Register)", 0.00, "JPY", 30000),
                        seat("VIP Lantern Experience", 8000.00, "JPY", 20000)
                ),
                List.of("japan", "sakura", "hanami", "spring", "2026"),
                CARLOS
        );

        createAndPublish(
                "Vienna New Year's Concert 2026",
                "Ring in the New Year with the world-famous Vienna Philharmonic at the Musikverein. " +
                "A century-old tradition broadcast to 100 countries.",
                "OTHER",
                future(330), future(330),
                venue("Wiener Musikverein", "Musikvereinsplatz 1", "Vienna", "Austria", 1744),
                List.of(
                        seat("Golden Hall Premium", 1000.00, "EUR", 200),
                        seat("Golden Hall", 600.00, "EUR", 900),
                        seat("Brahms Hall", 300.00, "EUR", 644)
                ),
                List.of("classical", "new-year", "vienna", "philharmonic"),
                ADMIN
        );

        // ── CANCELLED ─────────────────────────────────────────────────────────

        createAndCancel(
                "Drone Racing World Championship 2025",
                "The fastest pilots on the planet race FPV drones through a purpose-built indoor " +
                "course. Cancelled due to venue electrical fault.",
                "OTHER",
                future(38), future(38),
                venue("ExCeL London", "One Western Gateway", "London", "UK", 10000),
                List.of(
                        seat("Grandstand", 65.00, "GBP", 7000),
                        seat("VIP Pit Access", 200.00, "GBP", 3000)
                ),
                List.of("drone", "racing", "esports", "tech"),
                JANE
        );
    }

    // =========================================================================
    // Helpers — action shortcuts used by the seed methods above
    // =========================================================================

    /** Create an event through the service, then publish it. Goes through full validation + caching. */
    private void createAndPublish(String title, String description, String category,
                                   Instant dateTime, Instant endDateTime,
                                   VenueInfo venue, List<SeatCategoryInfo> seats,
                                   List<String> tags, OrganizerDto organizer) {
        EventDocument event = eventService.createEvent(
                buildRequest(title, description, category, dateTime, endDateTime, venue, seats, tags),
                organizer
        );
        eventService.publishEvent(event.getId());
        ApplicationLogger.logMessage(log, Level.DEBUG, "DataInitializer: published '{}'", title);
    }

    /** Create an event through the service and leave it in DRAFT status. */
    private void createDraft(String title, String description, String category,
                              Instant dateTime, Instant endDateTime,
                              VenueInfo venue, List<SeatCategoryInfo> seats,
                              List<String> tags, OrganizerDto organizer) {
        eventService.createEvent(
                buildRequest(title, description, category, dateTime, endDateTime, venue, seats, tags),
                organizer
        );
        ApplicationLogger.logMessage(log, Level.DEBUG, "DataInitializer: created draft '{}'", title);
    }

    /** Create an event, publish it, then cancel it. */
    private void createAndCancel(String title, String description, String category,
                                  Instant dateTime, Instant endDateTime,
                                  VenueInfo venue, List<SeatCategoryInfo> seats,
                                  List<String> tags, OrganizerDto organizer) {
        EventDocument event = eventService.createEvent(
                buildRequest(title, description, category, dateTime, endDateTime, venue, seats, tags),
                organizer
        );
        eventService.publishEvent(event.getId());
        eventService.cancelEvent(event.getId(), "Event cancelled — see description for details.");
        ApplicationLogger.logMessage(log, Level.DEBUG, "DataInitializer: cancelled '{}'", title);
    }

    /**
     * Create a past event in the COMPLETED state.
     *
     * <p>There is no {@code completeEvent()} service method (completion is handled by a
     * scheduled job in production), so we create the event through the service and then
     * mutate its status and timestamp directly via the repository. This keeps the document
     * structurally valid while representing historical events correctly.
     */
    private void createCompleted(String title, String description, String category,
                                  Instant dateTime, Instant endDateTime,
                                  VenueInfo venue, List<SeatCategoryInfo> seats,
                                  List<String> tags, OrganizerDto organizer) {
        // Use a future placeholder so the validator accepts it, then override after creation.
        Instant tempFuture = future(30);
        EventDocument event = eventService.createEvent(
                buildRequest(title, description, category, tempFuture, tempFuture, venue, seats, tags),
                organizer
        );
        eventService.publishEvent(event.getId());

        // Override dateTime and status via the repository.
        event.setDateTime(dateTime);
        event.setEndDateTime(endDateTime);
        event.setStatus(EventStatus.COMPLETED);
        eventRepository.save(event);
        ApplicationLogger.logMessage(log, Level.DEBUG, "DataInitializer: completed (past) '{}'", title);
    }

    // ── Request builder ───────────────────────────────────────────────────────

    private CreateEventRequest buildRequest(String title, String description, String category,
                                             Instant dateTime, Instant endDateTime,
                                             VenueInfo venue, List<SeatCategoryInfo> seats,
                                             List<String> tags) {
        return CreateEventRequest.newBuilder()
                .setTitle(title)
                .setDescription(description)
                .setCategory(category)
                .setDateTime(dateTime.toString())
                .setEndDateTime(endDateTime.toString())
                .setTimezone("UTC")
                .setVenue(venue)
                .addAllSeatCategories(seats)
                .addAllTags(tags)
                .build();
    }

    // ── Domain object builders ────────────────────────────────────────────────

    private VenueInfo venue(String name, String address, String city,
                             String country, int capacity) {
        return VenueInfo.newBuilder()
                .setName(name)
                .setAddress(address)
                .setCity(city)
                .setCountry(country)
                .setCapacity(capacity)
                .build();
    }

    private SeatCategoryInfo seat(String name, double price, String currency, int totalSeats) {
        return SeatCategoryInfo.newBuilder()
                .setName(name)
                .setPrice(price)
                .setCurrency(currency)
                .setTotalSeats(totalSeats)
                .build();
    }

    private Instant future(int daysFromNow) {
        return Instant.now().plus(daysFromNow, ChronoUnit.DAYS);
    }

    private Instant past(int daysAgo) {
        return Instant.now().minus(daysAgo, ChronoUnit.DAYS);
    }
}
