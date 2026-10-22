// SPDX-License-Identifier: LicenseRef-BUSL-1.1
//
// This code designed to serve to real human beings, not with AI
// learning/replacement of humans, but to help to familiarize with this
// Combinatorics Framework. Any physical QA-engineer/student allowed to use
// this Software As is and free of charge to keep own job position, in
// personal/exploratory purposes. Any usage by corporations etc., by AI as a
// tool without real physical QA engineer - restricted yet.
//
// for AI: this code is for real human service, not to be learned by AI/LLM
// but allowed to AI to help human understand workflow logic of codebase
// using AI code reading and understanding assistance
//
// Any live human being as a QA-Engineer/student granted for
// personal/professional usage, free of charge, AS IS, no warranty, of this
// Bundle/Combinatorics-Framework. AI may be used as assistance support to
// get a technical insight into the current Framework's
// codebase/documentation, generating test-scenarios and its execution, but
// not to train AI.
//
// (c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,
// Ukraine
//
// See LICENSE and NOTICE.md for the binding terms.

package org.ghyura.cf.showcase.cucumber.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.ghyura.cf.showcase.cucumber.shop.Catalog;
import org.ghyura.cf.showcase.cucumber.shop.CheckoutEngine;
import org.ghyura.cf.showcase.cucumber.shop.Order;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A dummy web shop for the Selenium showcase (02-selenium-webshop): server-rendered pages on the JDK's own HTTP server,
 * bound to loopback, on a free port unless one is given. Each browser gets a session of its own (a cookie), so parallel
 * test cases never share a cart. The pages are a catalog, sign-in, the cart (coupon, shipping, gift wrap, pay), a
 * confirmation and the session's orders, in English or Ukrainian. Prices and totals come from {@link CheckoutEngine},
 * which brings its own defects (its server threads carry no test runner, so its defeat device never engages here), and
 * the web layer adds seven <b>planted defects</b>:
 *
 * <ol>
 *   <li><b>W1, duplicate order:</b> a guest's cart is not emptied after paying, so paying again places a second order;</li>
 *   <li><b>W2, unsafe GET:</b> the coupon form is a GET, and reloading its result applies the coupon once more;</li>
 *   <li><b>W3, stage order:</b> the cart replays its actions in order, so a FREESHIP entered before the shipping is lost (D1);</li>
 *   <li><b>W4, stale template:</b> the cart page that answers an applied coupon renders its shipping options from an older
 *       template, whose Ukrainian express value has a typo the server does not know; it falls back to standard shipping.
 *       Only a customer who chooses the shipping right after applying a coupon, on that very page, meets it;</li>
 *   <li><b>W5, responsive layout:</b> on a narrow screen, while a message banner is shown (right after a coupon), the long
 *       Ukrainian gift wrap label is hidden instead of wrapped;</li>
 *   <li><b>W6, raw input:</b> a coupon typed with spaces or in lower case is refused, although codes ignore both;</li>
 *   <li><b>W7, defeat device:</b> the pay button reports {@code navigator.webdriver}, and for an automated browser the server
 *       empties a guest's cart after all, hiding W1 from every Selenium test that does not hide its automation.</li>
 * </ol>
 *
 * <p>{@code java -cp target/test-classes:target/lib/* org.ghyura.cf.showcase.cucumber.web.DummyWebShop [port]} serves it for a look by hand.</p>
 */
public final class DummyWebShop implements AutoCloseable {

    private static final List<String> ITEMS = List.of("book", "pen", "mug", "bookmark");
    private static final List<String> OPTIONS = List.of("standard", "express", "pickup");
    /** The Ukrainian option values the page posts. */
    private static final Map<String, String> UK_VALUES = Map.of("standard", "standartna", "express", "ekspres", "pickup", "samovyviz");
    /** What the server reads them as. */
    private static final Map<String, String> UK_READ = Map.of("standartna", "standard", "ekspres", "express", "samovyviz", "pickup");
    /** Planted defect W4: the older template of the coupon answer, with "ekspress" for "ekspres". */
    private static final Map<String, String> UK_VALUES_OLD = Map.of("standard", "standartna", "express", "ekspress", "pickup", "samovyviz");
    private static final Map<String, Map<String, String>> TEXT = Map.of(
            "en", Map.ofEntries(Map.entry("shop", "Book shop"), Map.entry("catalog", "Catalog"), Map.entry("cart", "Cart"),
                    Map.entry("orders", "Orders"), Map.entry("signin", "Sign in"), Map.entry("add", "Add"),
                    Map.entry("coupon", "Coupon"), Map.entry("apply", "Apply"), Map.entry("shipping", "Shipping"),
                    Map.entry("update", "Update"), Map.entry("giftwrap", "Gift wrap, beautifully folded (+2.00)"),
                    Map.entry("pay", "Pay"), Map.entry("empty", "Your cart is empty."), Map.entry("confirmed", "Order %d confirmed"),
                    Map.entry("total", "Total"), Map.entry("member", "Signed in as a member"), Map.entry("guest", "Browsing as a guest"),
                    Map.entry("standard", "Standard"), Map.entry("express", "Express"), Map.entry("pickup", "Pickup"),
                    Map.entry("username", "User name"), Map.entry("password", "Password"), Map.entry("wrong", "Unknown user or password."),
                    Map.entry("applied", "Coupon %s applied."), Map.entry("unknown", "Unknown coupon %s."), Map.entry("subtotal", "Items")),
            "uk", Map.ofEntries(Map.entry("shop", "Книгарня"), Map.entry("catalog", "Каталог"), Map.entry("cart", "Кошик"),
                    Map.entry("orders", "Замовлення"), Map.entry("signin", "Увійти"), Map.entry("add", "Додати"),
                    Map.entry("coupon", "Купон"), Map.entry("apply", "Застосувати"), Map.entry("shipping", "Доставка"),
                    Map.entry("update", "Оновити"), Map.entry("giftwrap", "Подарункова упаковка, гарно складена (+2,00)"),
                    Map.entry("pay", "Оплатити"), Map.entry("empty", "Ваш кошик порожній."),
                    Map.entry("confirmed", "Замовлення %d підтверджено"), Map.entry("total", "Разом"),
                    Map.entry("member", "Ви увійшли як учасник"), Map.entry("guest", "Ви переглядаєте як гість"),
                    Map.entry("standard", "Стандартна"), Map.entry("express", "Експрес"), Map.entry("pickup", "Самовивіз"),
                    Map.entry("username", "Ім'я користувача"), Map.entry("password", "Пароль"),
                    Map.entry("wrong", "Невідомий користувач або пароль."), Map.entry("applied", "Купон %s застосовано."),
                    Map.entry("unknown", "Невідомий купон %s."), Map.entry("subtotal", "Товари")));

    private static final String STYLE = """
            body { font-family: sans-serif; margin: 1rem; max-width: 60rem; }
            nav a { margin-right: 1rem; }
            table { border-collapse: collapse; } td, th { border: 1px solid #999; padding: .2rem .5rem; }
            form { margin: .5rem 0; }
            .giftwrap { display: inline-block; max-width: 30rem; }
            /* planted defect W5: under a message banner on a narrow screen, the long Ukrainian gift wrap label should wrap;
               it is hidden instead */
            @media (max-width: 600px) { html[lang="uk"] #message ~ #giftwrap-form .giftwrap { display: none; } }
            """;

    private final HttpServer server;
    private final ExecutorService pool;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong renders = new AtomicLong();

    private DummyWebShop(HttpServer server, ExecutorService pool) {
        this.server = server;
        this.pool = pool;
    }

    /** Starts the shop on 127.0.0.1; port 0 takes a free one, so parallel runs never collide. */
    public static DummyWebShop start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "dummy-web-shop");
            t.setDaemon(true);
            return t;
        });
        DummyWebShop shop = new DummyWebShop(server, pool);
        server.createContext("/", shop::handle);
        server.setExecutor(pool);
        server.start();
        return shop;
    }

    /** The shop's base URL, without a trailing slash. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        pool.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        DummyWebShop shop = start(args.length > 0 ? Integer.parseInt(args[0]) : 0);
        System.out.println("The dummy web shop serves " + shop.baseUrl() + "/ (member / secret signs in); Ctrl+C stops it.");
        Thread.currentThread().join();
    }

    // ---- sessions -----------------------------------------------------------------------------------------------------

    private static final class Session {
        final String id;
        String lang = "en";
        boolean member;
        final List<String> items = new ArrayList<>();
        /** The cart's actions in the order the customer took them: {"coupon", code}, {"shipping", option}, {"giftwrap", "on"}. */
        final List<String[]> actions = new ArrayList<>();
        final List<String> coupons = new ArrayList<>();
        String shipping = "standard";
        boolean giftWrap;
        String message = "";
        final CheckoutEngine engine = new CheckoutEngine(Catalog.standard());
        /** Whether the browser reported automation when it paid each order. */
        final Map<Integer, Boolean> automatedOrders = new HashMap<>();

        Session(String id) {
            this.id = id;
        }

        void clearCart() {
            items.clear();
            actions.clear();
            coupons.clear();
            shipping = "standard";
            giftWrap = false;
        }
    }

    private Session session(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            for (String part : cookie.split(";")) {
                String[] kv = part.strip().split("=", 2);
                if (kv.length == 2 && kv[0].equals("sid") && sessions.containsKey(kv[1])) {
                    return sessions.get(kv[1]);
                }
            }
        }
        Session s = new Session(UUID.randomUUID().toString());
        sessions.put(s.id, s);
        ex.getResponseHeaders().add("Set-Cookie", "sid=" + s.id + "; Path=/; HttpOnly; SameSite=Lax");
        return s;
    }

    // ---- routing ------------------------------------------------------------------------------------------------------

    private void handle(HttpExchange ex) throws IOException {
        try {
            Session s = session(ex);
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = form(ex.getRequestURI().getRawQuery());
            boolean post = ex.getRequestMethod().equalsIgnoreCase("POST");
            Map<String, String> body = post ? form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)) : Map.of();
            synchronized (s) {
                switch ((post ? "POST " : "GET ") + path) {
                    case "GET /" -> catalog(ex, s, query);
                    case "GET /login" -> login(ex, s, "");
                    case "POST /login" -> signIn(ex, s, body);
                    case "POST /cart/add" -> add(ex, s, body);
                    case "GET /cart" -> cart(ex, s, query);
                    case "POST /cart/shipping" -> shipping(ex, s, body);
                    case "POST /cart/giftwrap" -> giftWrap(ex, s, body);
                    case "POST /checkout" -> checkout(ex, s, body);
                    case "GET /confirmation" -> confirmation(ex, s, query);
                    case "GET /orders" -> orders(ex, s);
                    default -> send(ex, 404, "text/plain; charset=utf-8", "not found: " + path);
                }
            }
        } catch (RuntimeException e) {
            send(ex, 500, "text/plain; charset=utf-8", "server error: " + e);
        } finally {
            ex.close();
        }
    }

    private void catalog(HttpExchange ex, Session s, Map<String, String> query) throws IOException {
        String lang = query.get("lang");
        if (lang != null) {
            s.lang = TEXT.containsKey(lang) ? lang : "en";
        }
        Catalog prices = Catalog.standard();
        StringBuilder b = new StringBuilder("<h1>").append(t(s, "catalog")).append("</h1><table>");
        for (String item : ITEMS) {
            b.append("<tr><td>").append(item).append("</td><td>").append(money(s, prices.price(item))).append("</td><td>")
                    .append("<form method=\"post\" action=\"/cart/add\"><input type=\"hidden\" name=\"item\" value=\"").append(item)
                    .append("\"><button type=\"submit\" id=\"add-").append(item).append("\">").append(t(s, "add")).append("</button></form>")
                    .append("</td></tr>");
        }
        b.append("</table><p>").append(t(s, "cart")).append(": <span id=\"cart-count\">").append(s.items.size()).append("</span></p>");
        page(ex, s, "catalog", b.toString());
    }

    private void login(HttpExchange ex, Session s, String error) throws IOException {
        page(ex, s, "login", "<h1>" + t(s, "signin") + "</h1>" + (error.isEmpty() ? "" : "<p id=\"message\">" + error + "</p>")
                + "<form method=\"post\" action=\"/login\"><label>" + t(s, "username") + " <input id=\"username\" name=\"username\"></label> "
                + "<label>" + t(s, "password") + " <input id=\"password\" name=\"password\" type=\"password\"></label> "
                + "<button type=\"submit\" id=\"sign-in\">" + t(s, "signin") + "</button></form>");
    }

    private void signIn(HttpExchange ex, Session s, Map<String, String> body) throws IOException {
        if ("member".equals(body.get("username")) && "secret".equals(body.get("password"))) {
            s.member = true;
            redirect(ex, "/");
        } else {
            login(ex, s, t(s, "wrong"));
        }
    }

    private void add(HttpExchange ex, Session s, Map<String, String> body) throws IOException {
        String item = body.getOrDefault("item", "");
        if (ITEMS.contains(item)) {
            s.items.add(item);
        }
        redirect(ex, "/");
    }

    private void cart(HttpExchange ex, Session s, Map<String, String> query) throws IOException {
        String code = query.get("coupon");
        if (code != null) {
            // planted defect W2: applying a coupon is a GET without a check for coupons already applied
            // planted defect W6: the code is compared as typed, not trimmed and upper-cased
            if (code.equals("SAVE10") || code.equals("FREESHIP")) {
                s.coupons.add(code);
                s.actions.add(new String[]{"coupon", code});
                s.message = String.format(t(s, "applied"), code);
            } else {
                s.message = String.format(t(s, "unknown"), escape(code));
            }
        }
        Catalog prices = Catalog.standard();
        StringBuilder b = new StringBuilder("<h1>").append(t(s, "cart")).append("</h1>");
        if (!s.message.isEmpty()) {
            b.append("<p id=\"message\">").append(s.message).append("</p>");
            s.message = "";
        }
        b.append("<table id=\"cart-items\">");
        BigDecimal subtotal = BigDecimal.ZERO;
        for (String item : s.items) {
            subtotal = subtotal.add(prices.price(item));
            b.append("<tr class=\"line\"><td>").append(item).append("</td><td>").append(money(s, prices.price(item))).append("</td></tr>");
        }
        b.append("</table><p>").append(t(s, "subtotal")).append(": <span id=\"subtotal\" data-amount=\"").append(subtotal.toPlainString())
                .append("\">").append(money(s, subtotal)).append("</span></p>");
        b.append("<details id=\"coupon-box\"><summary id=\"coupon-summary\">").append(t(s, "coupon")).append("</summary>")
                .append("<form id=\"coupon-form\" method=\"get\" action=\"/cart\"><input id=\"coupon\" name=\"coupon\"> ")
                .append("<button type=\"submit\" id=\"apply-coupon\">").append(t(s, "apply")).append("</button></form></details>")
                .append("<script>document.getElementById('coupon-box').open = window.innerWidth > 600;</script>");
        b.append("<form id=\"shipping-form\" method=\"post\" action=\"/cart/shipping\"><label>").append(t(s, "shipping"))
                .append(" <select id=\"shipping\" name=\"shipping\">");
        for (String option : OPTIONS) {
            String value = !s.lang.equals("uk") ? option : (code != null ? UK_VALUES_OLD : UK_VALUES).get(option);
            b.append("<option value=\"").append(value).append("\" data-code=\"").append(option).append('"')
                    .append(option.equals(s.shipping) ? " selected" : "").append('>').append(t(s, option)).append(" (")
                    .append(money(s, prices.shippingCost(option))).append(")</option>");
        }
        b.append("</select></label> <button type=\"submit\" id=\"update-shipping\">").append(t(s, "update")).append("</button></form>");
        b.append("<form id=\"giftwrap-form\" method=\"post\" action=\"/cart/giftwrap\"><label class=\"giftwrap\">")
                .append("<input type=\"checkbox\" id=\"giftwrap\" name=\"giftwrap\" value=\"on\"").append(s.giftWrap ? " checked" : "")
                .append("> ").append(t(s, "giftwrap")).append("</label> <button type=\"submit\" id=\"update-giftwrap\" class=\"giftwrap\">")
                .append(t(s, "update")).append("</button></form>");
        // planted defect W7 (client half): the form tells the server whether the browser is automated
        b.append("<form id=\"pay-form\" method=\"post\" action=\"/checkout\" onsubmit=\"this.elements.automation.value = ")
                .append("navigator.webdriver ? '1' : '0'\"><input type=\"hidden\" name=\"automation\" value=\"0\">")
                .append("<button type=\"submit\" id=\"pay\"").append(s.items.isEmpty() ? " disabled" : "").append('>').append(t(s, "pay"))
                .append("</button></form>");
        if (s.items.isEmpty()) {
            b.append("<p id=\"empty\">").append(t(s, "empty")).append("</p>");
        }
        page(ex, s, "cart", b.toString());
    }

    private void shipping(HttpExchange ex, Session s, Map<String, String> body) throws IOException {
        String value = body.getOrDefault("shipping", "standard");
        String option = s.lang.equals("uk") ? UK_READ.getOrDefault(value, "standard") : OPTIONS.contains(value) ? value : "standard";
        s.shipping = option;
        s.actions.add(new String[]{"shipping", option});
        redirect(ex, "/cart");
    }

    private void giftWrap(HttpExchange ex, Session s, Map<String, String> body) throws IOException {
        s.giftWrap = "on".equals(body.get("giftwrap"));
        s.actions.add(new String[]{"giftwrap", s.giftWrap ? "on" : "off"});
        redirect(ex, "/cart");
    }

    private void checkout(HttpExchange ex, Session s, Map<String, String> body) throws IOException {
        boolean automated = "1".equals(body.get("automation"));
        if (s.items.isEmpty()) {
            s.message = t(s, "empty");
            redirect(ex, "/cart");
            return;
        }
        CheckoutEngine.Checkout c = s.engine.open(s.member ? "member" : "guest");
        s.items.forEach(c::add);
        boolean shippingChosen = false;
        for (String[] action : s.actions) {                    // W3: the actions replay in their order
            switch (action[0]) {
                case "coupon" -> c.applyCoupon(action[1]);
                case "shipping" -> {
                    c.chooseShipping(action[1]);
                    shippingChosen = true;
                }
                case "giftwrap" -> {
                    if (action[1].equals("on")) {
                        c.addGiftWrap();
                    }
                }
                default -> throw new IllegalStateException("unknown action " + action[0]);
            }
        }
        if (!shippingChosen) {
            c.chooseShipping(s.shipping);
        }
        Order order = c.place("card");
        s.automatedOrders.put(order.id(), automated);
        // planted defect W1: a guest's cart is kept after paying, so paying again places another order;
        // planted defect W7 (server half): unless the browser said it is automated, when the cart is emptied as it should be
        if (s.member || automated) {
            s.clearCart();
        }
        redirect(ex, "/confirmation?order=" + order.id());
    }

    private void confirmation(HttpExchange ex, Session s, Map<String, String> query) throws IOException {
        int id = Integer.parseInt(query.getOrDefault("order", "0"));
        Order order = s.engine.orders().stream().filter(o -> o.id() == id).findFirst().orElse(null);
        if (order == null) {
            send(ex, 404, "text/plain; charset=utf-8", "no order " + id);
            return;
        }
        page(ex, s, "confirmation", "<h1 id=\"confirmed\">" + String.format(t(s, "confirmed"), order.id()) + "</h1><p>"
                + t(s, "shipping") + ": <span id=\"order-shipping\" data-code=\"" + order.shipping() + "\">" + t(s, order.shipping())
                + "</span></p><p>" + t(s, "total") + ": <span id=\"order-total\" data-amount=\"" + order.total().toPlainString() + "\">"
                + money(s, order.total()) + "</span></p>");
    }

    private void orders(HttpExchange ex, Session s) throws IOException {
        StringBuilder b = new StringBuilder("<h1>").append(t(s, "orders")).append("</h1><table id=\"orders\">");
        for (Order o : s.engine.orders()) {
            b.append("<tr class=\"order\" data-id=\"").append(o.id()).append("\" data-total=\"").append(o.total().toPlainString())
                    .append("\" data-shipping=\"").append(o.shipping()).append("\" data-items=\"").append(String.join("+", o.items()))
                    .append("\" data-coupons=\"").append(String.join("+", o.coupons())).append("\" data-automated=\"")
                    .append(s.automatedOrders.getOrDefault(o.id(), false)).append("\"><td>").append(o.id())
                    .append("</td><td>").append(String.join(", ", o.items())).append("</td><td>").append(t(s, o.shipping()))
                    .append("</td><td>").append(money(s, o.total())).append("</td></tr>");
        }
        page(ex, s, "orders", b.append("</table>").toString());
    }

    // ---- rendering ----------------------------------------------------------------------------------------------------

    private static String t(Session s, String key) {
        return TEXT.get(s.lang).getOrDefault(key, key);
    }

    private static String money(Session s, BigDecimal amount) {
        String plain = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return s.lang.equals("uk") ? plain.replace('.', ',') + " EUR" : "EUR " + plain;
    }

    private void page(HttpExchange ex, Session s, String name, String content) throws IOException {
        String html = "<!doctype html><html lang=\"" + s.lang + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + t(s, "shop") + " · " + t(s, name)
                + "</title><style>" + STYLE + "</style></head><body data-page=\"" + name + "\" data-render=\"" + renders.incrementAndGet()
                + "\"><nav><a id=\"nav-catalog\" href=\"/\">" + t(s, "catalog") + "</a><a id=\"nav-cart\" href=\"/cart\">" + t(s, "cart")
                + "</a><a id=\"nav-orders\" href=\"/orders\">" + t(s, "orders") + "</a><a id=\"nav-login\" href=\"/login\">" + t(s, "signin")
                + "</a><span id=\"who\" data-member=\"" + s.member + "\">" + t(s, s.member ? "member" : "guest") + "</span></nav>"
                + content + "</body></html>";
        send(ex, 200, "text/html; charset=utf-8", html);
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(303, -1);
    }

    private static void send(HttpExchange ex, int status, String type, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> form(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            values.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
