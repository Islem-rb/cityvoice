import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MaintenanceLogListComponent } from './maintenance-log-list.component';

describe('MaintenanceLogListComponent', () => {
  let component: MaintenanceLogListComponent;
  let fixture: ComponentFixture<MaintenanceLogListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [MaintenanceLogListComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MaintenanceLogListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
