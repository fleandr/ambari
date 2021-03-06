/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {Component, OnInit, OnDestroy, Output, EventEmitter, ViewChild, ElementRef} from '@angular/core';
import * as $ from 'jquery';
import '@vendor/js/bootstrap-datetimepicker.min';
import {AppSettingsService} from '@app/services/storage/app-settings.service';

@Component({
  selector: 'date-picker',
  templateUrl: './date-picker.component.html'
})
export class DatePickerComponent implements OnInit, OnDestroy {

  constructor(private appSettings: AppSettingsService) {
    appSettings.getParameter('timeZone').subscribe(value => {
      this.destroyDatePicker();
      this.timeZone = value;
      if (this.datePickerElement) {
        this.createDatePicker();
      }
    });
  }

  ngOnInit() {
    this.createDatePicker();
  }

  ngOnDestroy() {
    this.destroyDatePicker();
  }

  @Output()
  timeChange: EventEmitter<number> = new EventEmitter();

  @ViewChild('datepicker')
  datePicker: ElementRef;

  private datePickerElement: any;

  private timeZone: string;

  private createDatePicker(): void {
    this.datePickerElement = $(this.datePicker.nativeElement);
    this.datePickerElement.datetimepicker({
      timeZone: this.timeZone
    });
    this.datePickerElement.on('dp.change', event => this.timeChange.emit(event.date));
  }

  private destroyDatePicker(): void {
    const datePicker = this.datePickerElement;
    if (datePicker) {
      datePicker.data('DateTimePicker').destroy();
    }
  }

}
