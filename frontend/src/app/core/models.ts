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
