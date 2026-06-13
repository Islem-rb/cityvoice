import { TestBed } from '@angular/core/testing';

import { MaintenanceLogService } from './maintenance-log.service';

describe('MaintenanceLogService', () => {
  let service: MaintenanceLogService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MaintenanceLogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
