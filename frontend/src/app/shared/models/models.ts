export interface User {
  id: string;
  username: string;
  email: string;
  emailVerified: boolean;
  enabled: boolean;
  firstName?: string;
  lastName?: string;
  createdAt?: string;
  phoneNumber?: string;
  country?: string;
  preferredLanguage?: string;
  preferredCurrency?: string;
  timezone?: string;
  profilePictureUrl?: string;
  emailNotifications?: boolean;
  smsNotifications?: boolean;
  billingAddress?: BillingAddress;
  roles: string[];
}

export interface BillingAddress {
  fullName?: string;
  line1?: string;
  line2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
}

export interface AuthPayload {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  refreshExpiresIn: number;
  tokenType: string;
  user?: User;
}

export interface VenueInfo {
  name: string;
  address?: string;
  city: string;
  country: string;
  latitude?: number;
  longitude?: number;
  capacity?: number;
}

export interface OrganizerInfo {
  userId: string;
  name: string;
  email: string;
}

export interface SeatCategory {
  name: string;
  price: string;
  currency: string;
  totalSeats: number;
  availableSeats: number;
}

export interface Event {
  id: string;
  title: string;
  description?: string;
  category: string;
  status: string;
  dateTime: string;
  endDateTime?: string;
  venue: VenueInfo;
  organizer: OrganizerInfo;
  seatCategories: SeatCategory[];
  images?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface EventConnection {
  events: Event[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
  smartResults: Event[];
}

export interface Booking {
  id: string;
  userId: string;
  eventId: string;
  eventTitle: string;
  status: string;
  seatCategory: string;
  quantity: number;
  unitPrice: string;
  totalPrice: string;
  currency: string;
  idempotencyKey: string;
  holdExpiresAt?: string;
  cancellationReason?: string;
  createdAt: string;
  updatedAt: string;
  event?: { images?: string[] };
}

export interface BookingConnection {
  bookings: Booking[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface Ticket {
  id: string;
  bookingId: string;
  eventId: string;
  userId: string;
  ticketNumber: string;
  qrCodeData: string;
  seatCategory: string;
  seatNumber?: string;
  status: string;
  eventTitle: string;
  createdAt: string;
}

export interface TicketConnection {
  tickets: Ticket[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

// ── Payments ────────────────────────────────────────────────────────────────

export interface PaymentIntent {
  paymentId: string;
  bookingId: string;
  externalPaymentId: string | null;
  clientSecret: string | null;   // null when no card entry is needed (e.g. already paid)
  status: string;                // PaymentStatus name (e.g. "PROCESSING", "COMPLETED")
  provider: string;              // 'stripe' | 'mock' — which card form to render
  publishableKey: string | null; // Stripe publishable key for the browser; null in mock mode
}
