import { Component, OnInit } from '@angular/core';
import { MaintenanceLog, MaintenanceLogService } from '../maintenance-log.service';
MaintenanceLogService
@Component({
  selector: 'app-maintenance',
  templateUrl: './maintenance-log-list.component.html'
})
export class MaintenanceLogListComponent implements OnInit {

  logs: MaintenanceLog[] = [];

  constructor(private maintenanceService: MaintenanceLogService) {}

  ngOnInit(): void {
    this.getLogs();
  }
  getLogs() {
    throw new Error('Method not implemented.');
  }

 
}