// Tipos de la API (espejo de los DTO del backend).

export type Role = 'CUSTOMER' | 'ADMIN';

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  sessionId: string;
  user: UserSummary;
}

export type AccountType = 'CHECKING' | 'SAVINGS' | 'SYSTEM';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  id: string;
  iban: string;
  type: AccountType;
  name: string;
  status: AccountStatus;
  balance: number;
  currency: string;
  dailyLimit?: number;
  goalAmount?: number;
  icon?: SavingsIcon;
  createdAt: string;
}

export type SavingsIcon =
  'piggy' | 'plane' | 'home' | 'car' | 'gift' | 'heart' | 'study' | 'umbrella' | 'star';

export type Category =
  | 'SALARY'
  | 'TRANSFER'
  | 'SAVINGS'
  | 'GROCERIES'
  | 'RESTAURANTS'
  | 'TRANSPORT'
  | 'SHOPPING'
  | 'LEISURE'
  | 'BILLS'
  | 'HOUSING'
  | 'HEALTH'
  | 'TRAVEL'
  | 'GIFT'
  | 'OTHER';

export type EntryType =
  | 'TRANSFER'
  | 'OWN_TRANSFER'
  | 'INCOMING_TRANSFER'
  | 'CARD_PAYMENT'
  | 'SAVINGS_MOVE'
  | 'WELCOME_GIFT';

export interface Movement {
  id: number;
  entryId: string;
  occurredAt: string;
  type: EntryType;
  amount: number;
  balanceAfter: number;
  category?: Category;
  counterpartyName?: string;
  counterpartyIban?: string;
  concept?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type TransferStatus = 'PENDING_SCA' | 'COMPLETED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';

export interface Transfer {
  id: string;
  fromAccountId: string;
  toIban: string;
  beneficiaryName: string;
  amount: number;
  concept?: string;
  status: TransferStatus;
  internal: boolean;
  scaRequired: boolean;
  scaExpiresAt?: string;
  rejectionReason?: string;
  createdAt: string;
  completedAt?: string;
}

export interface CreateTransfer {
  fromAccountId: string;
  toIban: string;
  beneficiaryName: string;
  amount: number;
  concept?: string;
}

export interface Payee {
  id: string;
  name: string;
  iban: string;
  alias?: string;
  internal: boolean;
  createdAt: string;
}

export interface IbanCheck {
  iban: string;
  valid: boolean;
  internal: boolean;
  savedPayee: boolean;
}

export type CardStatus = 'ACTIVE' | 'FROZEN' | 'CANCELLED';

export interface Card {
  id: string;
  accountId: string;
  holderName: string;
  last4: string;
  expiryMonth: number;
  expiryYear: number;
  status: CardStatus;
  onlineEnabled: boolean;
  monthlyLimit: number;
  spentThisMonth: number;
  createdAt: string;
}

export type DeclineReason =
  | 'CARD_FROZEN'
  | 'CARD_CANCELLED'
  | 'ONLINE_DISABLED'
  | 'MONTHLY_LIMIT'
  | 'DAILY_LIMIT'
  | 'INSUFFICIENT_FUNDS'
  | 'ACCOUNT_NOT_ACTIVE';

export interface CardTransaction {
  id: string;
  merchant: string;
  category: Category;
  amount: number;
  online: boolean;
  status: 'APPROVED' | 'DECLINED';
  declineReason?: DeclineReason;
  declineMessage?: string;
  createdAt: string;
}

export type NotificationType =
  | 'WELCOME'
  | 'SCA_CODE'
  | 'TRANSFER_RECEIVED'
  | 'TRANSFER_COMPLETED'
  | 'CARD_DECLINED'
  | 'ACCOUNT_FROZEN'
  | 'ACCOUNT_UNFROZEN'
  | 'SECURITY';

export interface AppNotification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}

export interface AnalyticsSummary {
  thisMonth: {
    income: number;
    expenses: number;
    net: number;
    savingsRate?: number;
    totalBalance: number;
    savingsBalance: number;
  };
  months: { month: string; income: number; expenses: number }[];
  categories: { category: Category; amount: number; share: number }[];
  balanceHistory: { date: string; balance: number }[];
}

export interface Session {
  id: string;
  startedAt: string;
  lastUsedAt: string;
  userAgent?: string;
  ipAddress?: string;
  current: boolean;
}

export interface AdminOverview {
  customers: number;
  accounts: number;
  customerDeposits: number;
  transfersToday: number;
  transferVolumeToday: number;
  cardApprovalsToday: number;
  cardDeclinesToday: number;
  pendingOutboxEvents: number;
  liveConnections: number;
  last7Days: { date: string; volume: number }[];
}

export interface AdminUser {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: 'ACTIVE' | 'BLOCKED';
  createdAt: string;
  totalBalance: number;
}

export interface AuditEntry {
  id: string;
  occurredAt: string;
  actorEmail?: string;
  action: string;
  targetType?: string;
  targetId?: string;
  ipAddress?: string;
  details?: string;
}
