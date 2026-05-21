export interface ContactRequest {
  contactType: 'EMAIL' | 'PHONE';
  contactValue: string;
  isPrimary: boolean;
}

export interface UserResponse {
  id: string;
  userType: string;
  fullName: string;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  active: boolean;
  contacts: { contactType: string; contactValue: string; isPrimary: boolean }[];
  addresses: any[];
  doctorProfile?: DoctorProfileResponse;
}

export interface DoctorProfileResponse {
  id: string;
  userId: string;
  fullName: string;
  licenseNumber: string;
  yearsOfExperience: number;
  consultationFee: number;
  verified: boolean;
  active: boolean;
}

export interface DoctorSearchResponse {
  id: string;
  userId: string;
  fullName: string;
  primarySpecialization: string;
  allSpecializations: string[];
  consultationFee: number;
  yearsOfExperience: number;
  verified: boolean;
  availableDays: string[];
}

export interface DoctorAvailability {
  id: string;
  doctorId: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  slotDuration: number;
  active: boolean;
}

export interface SlotResponse {
  id: string;
  doctorId: string;
  slotDate: string;
  startTime: string;
  endTime: string;
  status: string;
}

export interface AppointmentResponse {
  id: string;
  slotId: string;
  patientId: string;
  doctorId: string;
  status: string;
  bookedAt: string;
}

export interface BookingInitiated {
  workflowId: string;
  status: string;
  message: string;
}

export interface NotificationResponse {
  id: string;
  userId: string;
  channel: string;
  subject: string;
  body: string;
  status: string;
  createdAt: string;
}

export interface EmrState {
  patientId: string;
  currentDiagnosis: string;
  medications: string[];
  allergies: string[];
  conditions: string[];
  lastUpdated: string;
}

export interface EmrEvent {
  id: string;
  patientId: string;
  eventType: string;
  payload: any;
  occurredAt: string;
  recordedBy: string;
}

export interface PaymentIntent {
  id: string;
  clientSecret: string;
  amount: number;
  currency: string;
  status: string;
}

export interface Payment {
  id: string;
  appointmentId: string;
  amount: number;
  status: string;
  createdAt: string;
}

export interface AnalyticsDashboard {
  totalUsers: number;
  totalDoctors: number;
  totalAppointments: number;
  totalRevenue: number;
}

export interface DailyAppointment {
  date: string;
  count: number;
  revenue: number;
}

export interface DoctorPerformance {
  doctorId: string;
  name: string;
  appointmentCount: number;
  rating: number;
}

export interface Referral {
  id: string;
  patientId: string;
  fromDoctorId: string;
  toDoctorId: string;
  reason: string;
  status: string;
  createdAt: string;
}

export interface PatientSummary {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  appointments: AppointmentResponse[];
  referrals: Referral[];
  emrState?: EmrState;
}
