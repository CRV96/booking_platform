import { gql } from '@apollo/client/core';

// ── Auth ──────────────────────────────────────────────────────────────────────

export const AUTH_FIELDS = `
  accessToken
  refreshToken
  expiresIn
  refreshExpiresIn
  tokenType
  user {
    id username email firstName lastName roles
  }
`;

export const LOGIN = gql`
  mutation Login($input: LoginInput!) {
    login(input: $input) { ${AUTH_FIELDS} }
  }
`;

export const REGISTER = gql`
  mutation Register($input: RegisterInput!) {
    register(input: $input) { ${AUTH_FIELDS} }
  }
`;

export const REFRESH_TOKEN = gql`
  mutation RefreshToken($refreshToken: String!) {
    refreshToken(refreshToken: $refreshToken) { ${AUTH_FIELDS} }
  }
`;

export const LOGOUT = gql`
  mutation Logout($refreshToken: String!) {
    logout(refreshToken: $refreshToken) { success }
  }
`;

// ── User ──────────────────────────────────────────────────────────────────────

export const ME = gql`
  query Me {
    me {
      id username email emailVerified enabled
      firstName lastName createdAt
      phoneNumber country preferredLanguage preferredCurrency
      timezone profilePictureUrl emailNotifications smsNotifications
      billingAddress { fullName line1 line2 city state postalCode country }
      roles
    }
  }
`;

export const UPDATE_PROFILE = gql`
  mutation UpdateProfile($input: UpdateProfileInput!) {
    updateProfile(input: $input) {
      id username email firstName lastName
      phoneNumber country preferredLanguage preferredCurrency
      timezone emailNotifications smsNotifications
      billingAddress { fullName line1 line2 city state postalCode country }
      roles
    }
  }
`;

// ── Events ────────────────────────────────────────────────────────────────────

const EVENT_FIELDS = `
  id title description category status dateTime endDateTime
  venue { name address city country latitude longitude capacity }
  organizer { userId name email }
  seatCategories { name price currency totalSeats availableSeats }
  images
  createdAt updatedAt
`;

export const GET_EVENTS = gql`
  query GetEvents(
    $query: String $category: EventCategory $city: String
    $dateFrom: String $dateTo: String $page: Int $pageSize: Int $organizerId: String
    $aiSearch: Boolean
  ) {
    events(query: $query category: $category city: $city
           dateFrom: $dateFrom dateTo: $dateTo page: $page pageSize: $pageSize organizerId: $organizerId
           aiSearch: $aiSearch) {
      events { ${EVENT_FIELDS} }
      smartResults { ${EVENT_FIELDS} }
      totalCount page pageSize totalPages
    }
  }
`;

export const GET_EVENT = gql`
  query GetEvent($id: ID!) {
    event(id: $id) { ${EVENT_FIELDS} }
  }
`;

export const CREATE_EVENT = gql`
  mutation CreateEvent($input: CreateEventInput!) {
    createEvent(input: $input) { ${EVENT_FIELDS} }
  }
`;

export const UPDATE_EVENT = gql`
  mutation UpdateEvent($id: ID!, $input: UpdateEventInput!) {
    updateEvent(id: $id, input: $input) { ${EVENT_FIELDS} }
  }
`;

export const PUBLISH_EVENT = gql`
  mutation PublishEvent($id: ID!) {
    publishEvent(id: $id) { id status }
  }
`;

export const CANCEL_EVENT = gql`
  mutation CancelEvent($id: ID!) {
    cancelEvent(id: $id) { id status }
  }
`;

// ── Bookings ──────────────────────────────────────────────────────────────────

const BOOKING_FIELDS = `
  id userId eventId eventTitle status seatCategory quantity
  unitPrice totalPrice currency idempotencyKey
  holdExpiresAt cancellationReason createdAt updatedAt
  event { images }
`;

export const GET_MY_BOOKINGS = gql`
  query GetMyBookings($page: Int $pageSize: Int $status: BookingStatus) {
    myBookings(page: $page pageSize: $pageSize status: $status) {
      bookings { ${BOOKING_FIELDS} }
      totalCount page pageSize totalPages
    }
  }
`;

export const GET_BOOKING = gql`
  query GetBooking($id: ID!) {
    booking(id: $id) { ${BOOKING_FIELDS} }
  }
`;

// Organizer-only: all bookings for one of their events (for the stats page).
export const GET_EVENT_BOOKINGS = gql`
  query GetEventBookings($eventId: ID!) {
    eventBookings(eventId: $eventId) {
      id seatCategory quantity status totalPrice currency createdAt
    }
  }
`;

export const CREATE_BOOKING = gql`
  mutation CreateBooking($input: CreateBookingInput!) {
    createBooking(input: $input) { ${BOOKING_FIELDS} }
  }
`;

export const CANCEL_BOOKING = gql`
  mutation CancelBooking($id: ID!, $reason: String) {
    cancelBooking(id: $id, reason: $reason) { id status cancellationReason }
  }
`;

// Hard-delete an unpaid PENDING booking (abandoned checkout) — releases seats, no email.
export const DISCARD_BOOKING = gql`
  mutation DiscardBooking($id: ID!) {
    discardBooking(id: $id)
  }
`;

// ── Tickets ───────────────────────────────────────────────────────────────────

const TICKET_FIELDS = `
  id bookingId eventId userId ticketNumber qrCodeData
  seatCategory seatNumber status eventTitle createdAt
`;

export const GET_MY_TICKETS = gql`
  query GetMyTickets($page: Int $pageSize: Int) {
    myTickets(page: $page pageSize: $pageSize) {
      tickets { ${TICKET_FIELDS} }
      totalCount page pageSize totalPages
    }
  }
`;

export const GET_TICKETS_BY_BOOKING = gql`
  query GetTicketsByBooking($bookingId: ID!) {
    ticketsByBooking(bookingId: $bookingId) { ${TICKET_FIELDS} }
  }
`;

export const GET_TICKET = gql`
  query GetTicket($ticketNumber: String!) {
    ticket(ticketNumber: $ticketNumber) { ${TICKET_FIELDS} }
  }
`;

export const VALIDATE_TICKET = gql`
  mutation ValidateTicket($ticketNumber: String!) {
    validateTicket(ticketNumber: $ticketNumber) { id ticketNumber status }
  }
`;

export const CANCEL_TICKET = gql`
  mutation CancelTicket($ticketNumber: String!) {
    cancelTicket(ticketNumber: $ticketNumber) { id ticketNumber status }
  }
`;

// ── Payments ──────────────────────────────────────────────────────────────────

// One payment intent for an order covering several bookings.
export const CREATE_ORDER_PAYMENT_INTENT = gql`
  mutation CreateOrderPaymentIntent($orderId: ID!, $bookingIds: [ID!]!) {
    createOrderPaymentIntent(orderId: $orderId, bookingIds: $bookingIds) {
      paymentId bookingId externalPaymentId clientSecret status provider publishableKey
    }
  }
`;

// Mock mode only — simulate the payment outcome with a test card number.
export const CONFIRM_MOCK_PAYMENT = gql`
  mutation ConfirmMockPayment($bookingId: ID!, $cardNumber: String!) {
    confirmMockPayment(bookingId: $bookingId, cardNumber: $cardNumber) {
      paymentId bookingId externalPaymentId clientSecret status
    }
  }
`;

// ── Cart ────────────────────────────────────────────────────────────────────

const CART_FIELDS = `
  items { id eventId eventTitle seatCategory quantity unitPrice currency event { images } }
  totalPrice
  currency
`;

export const GET_CART = gql`
  query GetCart { cart { ${CART_FIELDS} } }
`;

export const ADD_TO_CART = gql`
  mutation AddToCart($input: AddToCartInput!) {
    addToCart(input: $input) { ${CART_FIELDS} }
  }
`;

export const UPDATE_CART_ITEM = gql`
  mutation UpdateCartItem($cartItemId: ID!, $quantity: Int!) {
    updateCartItem(cartItemId: $cartItemId, quantity: $quantity) { ${CART_FIELDS} }
  }
`;

export const REMOVE_FROM_CART = gql`
  mutation RemoveFromCart($cartItemId: ID!) {
    removeFromCart(cartItemId: $cartItemId) { ${CART_FIELDS} }
  }
`;

export const CLEAR_CART = gql`
  mutation ClearCart { clearCart { ${CART_FIELDS} } }
`;

// ── Lovelist ──────────────────────────────────────────────────────────────────

const LOVELIST_ITEM_FIELDS = `
  eventId
  createdAt
  event { id title category dateTime venue { city } images }
`;

export const GET_LOVELIST = gql`
  query GetLovelist { lovelist { ${LOVELIST_ITEM_FIELDS} } }
`;

export const ADD_FAVORITE = gql`
  mutation AddFavorite($eventId: ID!) {
    addFavorite(eventId: $eventId) { ${LOVELIST_ITEM_FIELDS} }
  }
`;

export const REMOVE_FAVORITE = gql`
  mutation RemoveFavorite($eventId: ID!) {
    removeFavorite(eventId: $eventId) { ${LOVELIST_ITEM_FIELDS} }
  }
`;
