import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/api.models';

/** Imagen de la biblioteca de la empresa (MediaAssetResponse). */
export interface MediaAsset {
  id: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  originalName: string | null;
  createdAt: string;
}

/** Progreso de una subida: porcentaje mientras sube y la imagen al terminar. */
export type UploadState = { progress: number; asset: null } | { progress: 100; asset: MediaAsset };

/** Tipos y tamano que acepta el backend (ImageValidator). El backend vuelve a comprobarlo todo. */
export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/** Biblioteca de imagenes. Endpoints: MediaController.java. */
@Injectable({ providedIn: 'root' })
export class MediaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/media`;

  upload(file: File): Observable<UploadState> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<MediaAsset>(this.url, body, { reportProgress: true, observe: 'events' }).pipe(
      filter((e: HttpEvent<MediaAsset>) => e.type === HttpEventType.UploadProgress || e.type === HttpEventType.Response),
      map((e): UploadState => {
        if (e.type === HttpEventType.Response) return { progress: 100, asset: e.body! };
        const total = e.type === HttpEventType.UploadProgress ? (e.total ?? file.size) : file.size;
        const loaded = e.type === HttpEventType.UploadProgress ? e.loaded : 0;
        return { progress: Math.min(99, Math.round((loaded / Math.max(1, total)) * 100)), asset: null };
      }),
    );
  }

  list(page = 0): Observable<Page<MediaAsset>> {
    return this.http.get<Page<MediaAsset>>(this.url, { params: { page, size: 24 } });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
