import { Pipe, PipeTransform } from '@angular/core';
import { formatIban } from './iban';
import {
  dayLabel,
  longDateTime,
  money,
  percent,
  shortDate,
  signedMoney,
  time,
  timeAgo,
} from './format';

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, signed = false): string {
    return signed ? signedMoney(value) : money(value);
  }
}

@Pipe({ name: 'iban' })
export class IbanPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatIban(value);
  }
}

@Pipe({ name: 'shortDate' })
export class ShortDatePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? shortDate(value) : '';
  }
}

@Pipe({ name: 'time' })
export class TimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? time(value) : '';
  }
}

@Pipe({ name: 'dayLabel' })
export class DayLabelPipe implements PipeTransform {
  transform(value: string): string {
    return dayLabel(value);
  }
}

@Pipe({ name: 'timeAgo' })
export class TimeAgoPipe implements PipeTransform {
  transform(value: string): string {
    return timeAgo(value);
  }
}

@Pipe({ name: 'longDateTime' })
export class LongDateTimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? longDateTime(value) : '';
  }
}

@Pipe({ name: 'percent' })
export class PercentPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return percent(value);
  }
}
