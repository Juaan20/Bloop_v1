export type VenueStatus = 'ACTIVE' | 'INACTIVE';

export interface Venue {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  addressLine: string;
  city: string;
  postalCode: string | null;
  country: string;
  phone: string | null;
  email: string | null;
  capacity: number;
  openingHours: string | null;
  imageUrl: string | null;
  status: VenueStatus;
  createdAt: string;
}

/** Cuerpo de alta/edicion (status solo se usa al editar). */
export interface VenuePayload {
  name: string;
  description: string | null;
  addressLine: string;
  city: string;
  postalCode: string | null;
  country: string;
  phone: string | null;
  email: string | null;
  capacity: number;
  openingHours: string | null;
  imageUrl: string | null;
  status?: VenueStatus;
}

export interface PublicVenue {
  name: string;
  slug: string;
  description: string | null;
  addressLine: string;
  city: string;
  postalCode: string | null;
  country: string;
  phone: string | null;
  email: string | null;
  openingHours: string | null;
  imageUrl: string | null;
}
