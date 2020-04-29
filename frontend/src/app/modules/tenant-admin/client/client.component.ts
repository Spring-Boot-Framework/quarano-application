import {ActivatedRoute, Router} from '@angular/router';
import {Component, OnInit, ViewChild} from '@angular/core';
import {CaseDetailDto} from '@models/case-detail';
import {Observable, Subject} from 'rxjs';
import {filter, map, take} from 'rxjs/operators';
import {ApiService} from '@services/api.service';
import {SnackbarService} from '@services/snackbar.service';
import {CaseActionDto} from '@models/case-action';
import {MatTabGroup} from '@angular/material/tabs';
import {StartTracking} from '@models/start-tracking';
import {HalResponse} from '@models/hal-response';


@Component({
  selector: 'app-clients',
  templateUrl: './client.component.html',
  styleUrls: ['./client.component.scss']
})
export class ClientComponent implements OnInit {
  caseDetail$: Observable<CaseDetailDto>;
  caseAction$: Observable<CaseActionDto>;

  trackingStart$$: Subject<StartTracking> = new Subject<StartTracking>();

  @ViewChild('tabs', {static: false})
  tabGroup: MatTabGroup;

  tabIndex = 0;

  constructor(
    private route: ActivatedRoute, private router: Router,
    private apiService: ApiService, private snackbarService: SnackbarService) {
  }

  ngOnInit(): void {
    this.caseDetail$ = this.route.data.pipe(
      map((data) => data.case));
    this.caseAction$ = this.route.data.pipe(map((data) => data.actions));

    this.caseDetail$.pipe(
      filter((data) => data !== null),
      filter((data) => data._links.hasOwnProperty('renew') && data._links.hasOwnProperty('start-tracking')),
      take(1)).subscribe((data) => {
        this.apiService
          .getApiCall<StartTracking>(data, 'start-tracking')
          .subscribe((sartTracking) => {
            this.trackingStart$$.next(sartTracking);
          });
      }
    );
  }

  hasOpenAnomalies(): Observable<boolean> {
    return this.caseAction$.pipe(map(a => (a.anomalies.health.length + a.anomalies.process.length) > 0));
  }


  saveCaseData(caseDetail: CaseDetailDto) {
    let saveData$: Observable<any>;
    if (!caseDetail.caseId) {
      saveData$ = this.apiService.createCase(caseDetail);
    } else {
      saveData$ = this.apiService.updateCase(caseDetail);
    }

    saveData$.pipe(take(1)).subscribe(() => {
      this.snackbarService.success('Persönliche Daten erfolgreich aktualisiert');
      this.router.navigate(['/tenant-admin/clients']);
    });
  }

  startTracking(caseDetail: CaseDetailDto) {

    this.apiService.putApiCall<StartTracking>(caseDetail, 'start-tracking')
      .subscribe((data) => {
        this.trackingStart$$.next(data);
        this.tabIndex = 3;
      });
  }

  renewTracking(tracking: HalResponse) {
    this.apiService.putApiCall<StartTracking>(tracking, 'renew')
      .subscribe((data) => {
        this.trackingStart$$.next(data);
        this.tabIndex = 3;
      });
  }
}