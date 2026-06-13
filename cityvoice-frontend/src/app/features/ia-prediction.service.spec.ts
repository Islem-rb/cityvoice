import { TestBed } from '@angular/core/testing';

import { IaPredictionService } from './ia-prediction.service';

describe('IaPredictionService', () => {
  let service: IaPredictionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(IaPredictionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
