const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export type AppointmentResponse = {
  id: number;
  vehicleId: number;
  vehicleVin: string;
  customerId: number;
  technicianId: number;
  technicianName: string;
  serviceBayId: number;
  bayNumber: string;
  serviceType: string;
  startTime: string;
  endTime: string;
  status: string;
};

export type ErrorResponse = {
  status: number;
  error: string;
  message: string;
  correlationId: string | null;
  timestamp: string;
  fieldErrors: string[];
};

export type CreateAppointmentRequest = {
  vehicleId?: number;
  serviceType: string;
  dealershipId: number;
  desiredStart: string;
  desiredEnd: string;
  technicianId?: number;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  vehicleVin?: string;
  vehicleMake?: string;
  vehicleModel?: string;
};

export type BusyWindow = {
  startTime: string;
  endTime: string;
};

export type TechnicianAvailability = {
  technicianId: number;
  technicianName: string;
  date: string;
  busyWindows: BusyWindow[];
};

export class ApiError extends Error {
  constructor(public body: ErrorResponse) {
    super(body.message);
  }
}

async function parseOrThrow<T>(res: Response): Promise<T> {
  const body = await res.json();
  if (!res.ok) {
    throw new ApiError(body as ErrorResponse);
  }
  return body as T;
}

export function createAppointment(
  request: CreateAppointmentRequest,
): Promise<AppointmentResponse> {
  return fetch(`${API_BASE_URL}/appointments`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  }).then((res) => parseOrThrow<AppointmentResponse>(res));
}

export function getAppointment(id: string): Promise<AppointmentResponse> {
  return fetch(`${API_BASE_URL}/appointments/${id}`).then((res) =>
    parseOrThrow<AppointmentResponse>(res),
  );
}

export function getTechnicianAvailability(
  dealershipId: number,
  serviceType: string,
  date: string,
): Promise<TechnicianAvailability[]> {
  const params = new URLSearchParams({
    dealershipId: String(dealershipId),
    serviceType,
    date,
  });
  return fetch(`${API_BASE_URL}/technicians/availability?${params}`).then((res) =>
    parseOrThrow<TechnicianAvailability[]>(res),
  );
}

export function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    const fields = err.body.fieldErrors.length
      ? ` (${err.body.fieldErrors.join(", ")})`
      : "";
    return `${err.body.status} ${err.body.error}: ${err.body.message}${fields}`;
  }
  return `Could not reach the API at ${API_BASE_URL}. Is the backend running (docker compose up --build)?`;
}
