package com.booking.platform.user_service.init;

import com.booking.platform.user_service.constants.UserAttributes;
import com.booking.platform.user_service.properties.KeycloakProperties;
import com.booking.platform.user_service.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * Seeds Keycloak with sample users on startup.
 *
 * <p>Active only in the {@code dev} profile. Idempotent — if more than 4 users
 * already exist (the 4 test users created by the realm import) the initializer
 * exits immediately, so restarting the service does not create duplicates.
 *
 * <p>Creates two types of accounts:
 * <ul>
 *   <li>50 customers — assigned to the {@code customers} group via
 *       {@link KeycloakUserService#createUser}, which already hard-wires that group.
 *       Each customer has a realistic set of profile attributes (phone, country,
 *       language, currency, timezone, date of birth).</li>
 *   <li>10 employees — assigned to the {@code employees} group directly through
 *       the Keycloak Admin API (the {@code createUser} service method only supports
 *       the customers group). Employees are given a work email and a standard
 *       internal attribute set.</li>
 * </ul>
 *
 * <p>All passwords follow the pattern {@code <firstName>Pass1!} so they satisfy
 * the default Keycloak password policy and are easy to remember during manual E2E
 * verification.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String GROUP_CUSTOMERS = "customers";
    private static final String GROUP_EMPLOYEES = "employees";
    private static final int  SKIP_THRESHOLD   = 4; // realm-imported test users

    private final KeycloakUserService keycloakUserService;
    private final Keycloak            keycloak;
    private final KeycloakProperties  keycloakProperties;

    // =========================================================================
    // Entry point
    // =========================================================================

    @Override
    public void run(ApplicationArguments args) {
        int existing = keycloakUserService.getUserCount(null);
        if (existing > SKIP_THRESHOLD) {
            log.info("DataInitializer: {} users already exist — skipping seed", existing);
            return;
        }

        log.info("DataInitializer: seeding 50 customers and 10 employees...");

        seedCustomers();
        seedEmployees();

        log.info("DataInitializer: seeding complete — {} users now in Keycloak",
                keycloakUserService.getUserCount(null));
    }

    // =========================================================================
    // CUSTOMERS — 50 accounts
    // Created via KeycloakUserService which auto-assigns the "customers" group.
    // =========================================================================

    private void seedCustomers() {

        // ── Europe ────────────────────────────────────────────────────────────

        createCustomer("luca.rossi",     "Luca",     "Rossi",     "luca.rossi@gmail.com",
                "LucaPass1!", "+39 02 1234 5678", "Italy",   "it", "EUR", "Europe/Rome",          "1990-03-15");
        createCustomer("emma.müller",    "Emma",     "Müller",    "emma.mueller@web.de",
                "EmmaPass1!", "+49 30 9876 5432", "Germany", "de", "EUR", "Europe/Berlin",        "1988-07-22");
        createCustomer("sophie.martin",  "Sophie",   "Martin",    "sophie.martin@laposte.net",
                "SophiePass1!", "+33 1 23 45 67 89", "France", "fr", "EUR", "Europe/Paris",      "1995-11-08");
        createCustomer("pablo.garcia",   "Pablo",    "García",    "pablo.garcia@correo.es",
                "PabloPass1!", "+34 91 123 4567",  "Spain",   "es", "EUR", "Europe/Madrid",       "1992-04-30");
        createCustomer("ana.popescu",    "Ana",      "Popescu",   "ana.popescu@yahoo.ro",
                "AnaPass1!",   "+40 21 987 6543",  "Romania", "ro", "RON", "Europe/Bucharest",    "1997-09-12");
        createCustomer("piotr.kowalski", "Piotr",    "Kowalski",  "piotr.kowalski@wp.pl",
                "PiotrPass1!", "+48 22 456 7890",  "Poland",  "pl", "PLN", "Europe/Warsaw",       "1985-01-25");
        createCustomer("elena.ivanova",  "Elena",    "Ivanova",   "elena.ivanova@mail.ru",
                "ElenaPass1!", "+7 495 123 4567",  "Russia",  "ru", "RUB", "Europe/Moscow",       "1993-06-18");
        createCustomer("marco.ferrari",  "Marco",    "Ferrari",   "marco.ferrari@libero.it",
                "MarcoPass1!", "+39 06 2345 6789", "Italy",   "it", "EUR", "Europe/Rome",          "1980-12-03");
        createCustomer("anna.svensson",  "Anna",     "Svensson",  "anna.svensson@hotmail.se",
                "AnnaPass1!",  "+46 8 765 4321",   "Sweden",  "sv", "SEK", "Europe/Stockholm",    "1991-02-14");
        createCustomer("tom.de.vries",   "Tom",      "De Vries",  "tom.devries@ziggo.nl",
                "TomPass1!",   "+31 20 987 6543",  "Netherlands", "nl", "EUR", "Europe/Amsterdam","1987-08-29");

        // ── Americas ──────────────────────────────────────────────────────────

        createCustomer("james.wilson",   "James",    "Wilson",    "james.wilson@gmail.com",
                "JamesPass1!", "+1 212 555 0101",  "USA",     "en", "USD", "America/New_York",    "1983-05-17");
        createCustomer("maria.rodriguez","Maria",    "Rodríguez", "maria.rodriguez@hotmail.com",
                "MariaPass1!", "+52 55 1234 5678", "Mexico",  "es", "MXN", "America/Mexico_City", "1996-10-05");
        createCustomer("lucas.oliveira", "Lucas",    "Oliveira",  "lucas.oliveira@uol.com.br",
                "LucasPass1!", "+55 11 91234 5678","Brazil",  "pt", "BRL", "America/Sao_Paulo",   "1994-03-21");
        createCustomer("sarah.johnson",  "Sarah",    "Johnson",   "sarah.johnson@yahoo.com",
                "SarahPass1!", "+1 310 555 0202",  "USA",     "en", "USD", "America/Los_Angeles", "1989-07-09");
        createCustomer("carlos.mendez",  "Carlos",   "Méndez",    "carlos.mendez@gmail.com",
                "CarlosPass1!","+54 11 4567 8901", "Argentina","es","ARS", "America/Argentina/Buenos_Aires","1986-11-27");
        createCustomer("emily.brown",    "Emily",    "Brown",     "emily.brown@outlook.com",
                "EmilyPass1!", "+1 647 555 0303",  "Canada",  "en", "CAD", "America/Toronto",     "1998-01-13");
        createCustomer("diego.herrera",  "Diego",    "Herrera",   "diego.herrera@gmail.com",
                "DiegoPass1!", "+57 1 234 5678",   "Colombia","es", "COP", "America/Bogota",      "1990-09-04");
        createCustomer("jessica.lee",    "Jessica",  "Lee",       "jessica.lee@icloud.com",
                "JessicaPass1!","+1 416 555 0404", "Canada",  "en", "CAD", "America/Toronto",     "1993-04-26");
        createCustomer("miguel.santos",  "Miguel",   "Santos",    "miguel.santos@gmail.com",
                "MiguelPass1!","+56 2 2345 6789",  "Chile",   "es", "CLP", "America/Santiago",    "1984-12-15");
        createCustomer("ashley.taylor",  "Ashley",   "Taylor",    "ashley.taylor@gmail.com",
                "AshleyPass1!","+1 305 555 0505",  "USA",     "en", "USD", "America/New_York",    "1992-06-08");

        // ── Asia Pacific ──────────────────────────────────────────────────────

        createCustomer("yuki.tanaka",    "Yuki",     "Tanaka",    "yuki.tanaka@yahoo.co.jp",
                "YukiPass1!",  "+81 3 1234 5678",  "Japan",   "ja", "JPY", "Asia/Tokyo",          "1991-03-30");
        createCustomer("wei.zhang",      "Wei",      "Zhang",     "wei.zhang@163.com",
                "WeiPass1!",   "+86 10 8765 4321", "China",   "zh", "CNY", "Asia/Shanghai",       "1988-08-08");
        createCustomer("priya.sharma",   "Priya",    "Sharma",    "priya.sharma@gmail.com",
                "PriyaPass1!", "+91 98765 43210",  "India",   "hi", "INR", "Asia/Kolkata",        "1995-02-17");
        createCustomer("min.jun.kim",    "Min-jun",  "Kim",       "minjun.kim@naver.com",
                "MinJunPass1!","+82 2 3456 7890",  "South Korea","ko","KRW","Asia/Seoul",          "1993-10-22");
        createCustomer("arjun.patel",    "Arjun",    "Patel",     "arjun.patel@gmail.com",
                "ArjunPass1!", "+91 99876 54321",  "India",   "hi", "INR", "Asia/Kolkata",        "1987-05-11");
        createCustomer("li.mei",         "Li",       "Mei",       "li.mei@qq.com",
                "LiMeiPass1!", "+86 21 6789 1234", "China",   "zh", "CNY", "Asia/Shanghai",       "1996-07-03");
        createCustomer("haruto.suzuki",  "Haruto",   "Suzuki",    "haruto.suzuki@docomo.ne.jp",
                "HarutoPass1!","+81 6 2345 6789",  "Japan",   "ja", "JPY", "Asia/Tokyo",          "1990-11-19");
        createCustomer("nurul.aisyah",   "Nurul",    "Aisyah",    "nurul.aisyah@gmail.com",
                "NurulPass1!", "+60 3 2345 6789",  "Malaysia","ms", "MYR", "Asia/Kuala_Lumpur",   "1994-04-07");
        createCustomer("siti.rahayu",    "Siti",     "Rahayu",    "siti.rahayu@yahoo.com",
                "SitiPass1!",  "+62 21 8765 4321", "Indonesia","id","IDR", "Asia/Jakarta",        "1989-09-25");
        createCustomer("nguyen.thu",     "Nguyen",   "Thu",       "nguyen.thu@gmail.com",
                "NguyenPass1!","+84 24 3456 7890", "Vietnam", "vi", "VND", "Asia/Ho_Chi_Minh",    "1997-01-31");

        // ── Middle East & Africa ──────────────────────────────────────────────

        createCustomer("omar.hassan",    "Omar",     "Hassan",    "omar.hassan@gmail.com",
                "OmarPass1!",  "+20 2 2345 6789",  "Egypt",   "ar", "EGP", "Africa/Cairo",        "1986-06-14");
        createCustomer("fatima.ali",     "Fatima",   "Ali",       "fatima.ali@hotmail.com",
                "FatimaPass1!","+971 4 345 6789",  "UAE",     "ar", "AED", "Asia/Dubai",          "1992-02-28");
        createCustomer("khalid.alfarsi", "Khalid",   "Al Farsi",  "khalid.alfarsi@gmail.com",
                "KhalidPass1!","+966 11 456 7890", "Saudi Arabia","ar","SAR","Asia/Riyadh",       "1984-10-09");
        createCustomer("amina.diallo",   "Amina",    "Diallo",    "amina.diallo@gmail.com",
                "AminaPass1!", "+221 33 123 4567", "Senegal", "fr", "XOF", "Africa/Dakar",        "1998-03-16");
        createCustomer("kofi.mensah",    "Kofi",     "Mensah",    "kofi.mensah@yahoo.com",
                "KofiPass1!",  "+233 30 234 5678", "Ghana",   "en", "GHS", "Africa/Accra",        "1990-07-21");

        // ── More mixed profiles ───────────────────────────────────────────────

        createCustomer("olivia.smith",   "Olivia",   "Smith",     "olivia.smith@gmail.com",
                "OliviaPass1!","+44 20 7946 0958", "UK",      "en", "GBP", "Europe/London",       "1995-05-03");
        createCustomer("noah.jones",     "Noah",     "Jones",     "noah.jones@outlook.com",
                "NoahPass1!",  "+44 161 234 5678", "UK",      "en", "GBP", "Europe/London",       "1991-12-19");
        createCustomer("charlotte.white","Charlotte","White",     "charlotte.white@icloud.com",
                "CharlottePass1!","+61 2 9876 5432","Australia","en","AUD","Australia/Sydney",     "1993-08-11");
        createCustomer("liam.anderson",  "Liam",     "Anderson",  "liam.anderson@gmail.com",
                "LiamPass1!",  "+61 3 8765 4321",  "Australia","en","AUD","Australia/Melbourne",  "1988-04-02");
        createCustomer("mia.thompson",   "Mia",      "Thompson",  "mia.thompson@gmail.com",
                "MiaPass1!",   "+64 9 876 5432",   "New Zealand","en","NZD","Pacific/Auckland",   "1996-10-27");
        createCustomer("ethan.clark",    "Ethan",    "Clark",     "ethan.clark@yahoo.com",
                "EthanPass1!", "+1 512 555 0606",  "USA",     "en", "USD", "America/Chicago",     "1982-02-06");
        createCustomer("isabella.hall",  "Isabella", "Hall",      "isabella.hall@gmail.com",
                "IsabellaPass1!","+1 617 555 0707","USA",     "en", "USD", "America/New_York",    "1999-06-23");
        createCustomer("mason.young",    "Mason",    "Young",     "mason.young@hotmail.com",
                "MasonPass1!", "+27 11 234 5678",  "South Africa","en","ZAR","Africa/Johannesburg","1987-09-16");
        createCustomer("zoe.king",       "Zoe",      "King",      "zoe.king@gmail.com",
                "ZoePass1!",   "+353 1 234 5678",  "Ireland", "en", "EUR", "Europe/Dublin",       "1994-11-04");
        createCustomer("lucas.petit",    "Lucas",    "Petit",     "lucas.petit@orange.fr",
                "LucasPass1!","+" + "33 6 12 34 56 78","France","fr","EUR","Europe/Paris",          "1989-03-08");
        createCustomer("andrei.ionescu", "Andrei",   "Ionescu",   "andrei.ionescu@gmail.com",
                "AndreiPass1!","+40 72 345 6789",  "Romania", "ro", "RON", "Europe/Bucharest",    "1985-07-17");
        createCustomer("radu.gheorghe",  "Radu",     "Gheorghe",  "radu.gheorghe@yahoo.ro",
                "RaduPass1!",  "+40 74 567 8901",  "Romania", "ro", "RON", "Europe/Bucharest",    "1992-01-29");
        createCustomer("ioana.dumitru",  "Ioana",    "Dumitru",   "ioana.dumitru@gmail.com",
                "IoanaPass1!", "+40 76 789 0123",  "Romania", "ro", "RON", "Europe/Bucharest",    "1997-05-20");
        createCustomer("stefan.popa",    "Stefan",   "Popa",      "stefan.popa@outlook.com",
                "StefanPass1!","+40 73 456 7890",  "Romania", "ro", "RON", "Europe/Bucharest",    "1983-08-31");

        log.info("DataInitializer: 50 customers created");
    }

    // =========================================================================
    // EMPLOYEES — 10 accounts
    // Created directly via the Keycloak Admin API with the "employees" group.
    // =========================================================================

    private void seedEmployees() {
        createEmployee("alice.walker",   "Alice",    "Walker",    "alice.walker@booking.platform",   "AlicePass1!");
        createEmployee("bob.carter",     "Bob",      "Carter",    "bob.carter@booking.platform",     "BobPass1!");
        createEmployee("claire.murphy",  "Claire",   "Murphy",    "claire.murphy@booking.platform",  "ClairePass1!");
        createEmployee("david.nguyen",   "David",    "Nguyen",    "david.nguyen@booking.platform",   "DavidPass1!");
        createEmployee("eve.robinson",   "Eve",      "Robinson",  "eve.robinson@booking.platform",   "EvePass1!");
        createEmployee("frank.bell",     "Frank",    "Bell",      "frank.bell@booking.platform",     "FrankPass1!");
        createEmployee("grace.foster",   "Grace",    "Foster",    "grace.foster@booking.platform",   "GracePass1!");
        createEmployee("henry.price",    "Henry",    "Price",     "henry.price@booking.platform",    "HenryPass1!");
        createEmployee("iris.morgan",    "Iris",     "Morgan",    "iris.morgan@booking.platform",    "IrisPass1!");
        createEmployee("jack.reed",      "Jack",     "Reed",      "jack.reed@booking.platform",      "JackPass1!");

        log.info("DataInitializer: 10 employees created");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a customer account by delegating to {@link KeycloakUserService#createUser},
     * which automatically assigns the {@code customers} group.
     */
    private void createCustomer(String username, String firstName, String lastName,
                                 String email, String password,
                                 String phone, String country,
                                 String language, String currency, String timezone,
                                 String dateOfBirth) {
        try {
            Map<String, String> attributes = Map.of(
                    UserAttributes.PHONE_NUMBER,          phone,
                    UserAttributes.COUNTRY,               country,
                    UserAttributes.PREFERRED_LANGUAGE,    language,
                    UserAttributes.PREFERRED_CURRENCY,    currency,
                    UserAttributes.TIMEZONE,              timezone,
                    UserAttributes.DATE_OF_BIRTH,         dateOfBirth,
                    UserAttributes.EMAIL_NOTIFICATIONS,   "true",
                    UserAttributes.SMS_NOTIFICATIONS,     "false"
            );
            keycloakUserService.createUser(email, password, firstName, lastName, attributes);
            log.debug("DataInitializer: customer created — {}", username);
        } catch (Exception e) {
            log.warn("DataInitializer: skipping customer '{}' — {}", username, e.getMessage());
        }
    }

    /**
     * Creates an employee account directly via the Keycloak Admin API, assigning
     * the {@code employees} group.  The {@link KeycloakUserService#createUser} method
     * only supports the {@code customers} group, so employees are seeded through the
     * raw admin client instead.
     */
    private void createEmployee(String username, String firstName, String lastName,
                                 String email, String password) {
        try {
            UsersResource usersResource = keycloak
                    .realm(keycloakProperties.realm())
                    .users();

            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setGroups(List.of(GROUP_EMPLOYEES));

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            user.setCredentials(List.of(credential));

            try (Response response = usersResource.create(user)) {
                if (response.getStatus() == 201) {
                    log.debug("DataInitializer: employee created — {}", username);
                } else {
                    log.warn("DataInitializer: failed to create employee '{}' — HTTP {}",
                            username, response.getStatus());
                }
            }
        } catch (Exception e) {
            log.warn("DataInitializer: skipping employee '{}' — {}", username, e.getMessage());
        }
    }
}
