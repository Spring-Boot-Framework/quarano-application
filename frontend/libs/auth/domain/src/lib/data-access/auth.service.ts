import { API_URL } from '@qro/shared/util-data-access';
import { HttpClient } from '@angular/common/http';
import { ChangePasswordDto } from './../model/change-password';
import { Injectable, Inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UserDto } from '../model/user';
import { map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  constructor(private httpClient: HttpClient, @Inject(API_URL) private apiUrl: string) {}

  changePassword(dto: ChangePasswordDto) {
    return this.httpClient.put(`${this.apiUrl}/api/user/me/password`, dto);
  }

  checkUsername(username: string): Observable<any> {
    return this.httpClient.get(`${this.apiUrl}/api/registration/checkusername/${username}`);
  }

  login(username: string, password: string): Observable<any> {
    return this.httpClient.post(`${this.apiUrl}/login`, { username, password }, { observe: 'response' });
  }

  getMe(): Observable<UserDto> {
    return this.httpClient.get<UserDto>(`${this.apiUrl}/api/user/me`);
  }
}
