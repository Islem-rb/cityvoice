import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminpersonnelComponent } from './adminpersonnel.component';

describe('AdminpersonnelComponent', () => {
  let component: AdminpersonnelComponent;
  let fixture: ComponentFixture<AdminpersonnelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [AdminpersonnelComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminpersonnelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
